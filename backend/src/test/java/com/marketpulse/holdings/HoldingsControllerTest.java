package com.marketpulse.holdings;

import com.marketpulse.config.MarketPulseProperties;
import com.marketpulse.market.MarketDataClient;
import com.marketpulse.market.TestCandles;
import com.marketpulse.reports.ReportStore;
import com.marketpulse.signals.TradeStyle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HoldingsController.class)
@Import(HoldingsControllerTest.Fixtures.class)
@EnableConfigurationProperties(MarketPulseProperties.class)
@TestPropertySource(properties = "marketpulse.dashboard.token=dash-token")
class HoldingsControllerTest {

    private static final String AUTH = "Bearer dash-token";

    @TestConfiguration
    static class Fixtures {
        @Bean ReportStore reportStore() { return new ReportStore(10); }
        // 01:00 IST on 12 Nov = 19:30 UTC on 11 Nov: the server's UTC date is still "yesterday"
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-11-11T19:30:00Z"), ZoneId.of("UTC")); }
    }

    @Autowired private MockMvc mvc;
    @MockitoBean private HoldingRepository repository;
    @MockitoBean private MarketDataClient market;

    @Test
    void listsHoldings() throws Exception {
        when(repository.findAll()).thenReturn(List.of(new Holding(1L, "TCS", 5, 3100, LocalDate.of(2026, 10, 1),
                TradeStyle.POSITIONAL, null, null, Instant.EPOCH)));

        mvc.perform(get("/api/holdings").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdings[0].symbol").value("TCS"))
                .andExpect(jsonPath("$.holdings[0].buyDate").value("2026-10-01"))
                .andExpect(jsonPath("$.latestCheck").doesNotExist());
    }

    @Test
    void addsAHoldingAfterCheckingTheSymbolHasPriceData() throws Exception {
        when(market.dailyCandles("AXISBANK")).thenReturn(TestCandles.trend(400, 1000, 0.001, 1_000, LocalDate.of(2026, 11, 11)));
        when(repository.add(any())).thenAnswer(inv -> {
            Holding h = inv.getArgument(0);
            return new Holding(7L, h.symbol(), h.quantity(), h.buyPrice(), h.buyDate(), h.horizon(), h.stopLoss(),
                    h.target(), h.createdAt());
        });

        mvc.perform(post("/api/holdings").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol":"axisbank","quantity":20,"buyPrice":1290.5,"buyDate":"2026-11-03",
                                 "horizon":"SWING","stopLoss":1240}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.symbol").value("AXISBANK"))
                .andExpect(jsonPath("$.stopLoss").value(1240.0))
                .andExpect(jsonPath("$.target").doesNotExist());
    }

    @Test
    void rejectsUnknownSymbols() throws Exception {
        when(market.dailyCandles("NOSUCH")).thenThrow(new IllegalStateException("No chart data"));

        mvc.perform(post("/api/holdings").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol":"NOSUCH","quantity":1,"buyPrice":10,"buyDate":"2026-11-03","horizon":"SWING"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No NSE price data found for NOSUCH - check the symbol"));
        verify(repository, never()).add(any());
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        mvc.perform(post("/api/holdings").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol":"TCS","quantity":0,"buyPrice":-5,"buyDate":"2026-11-03","horizon":"SWING"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("quantity")));
    }

    @Test
    void btstIsNotAHoldingHorizon() throws Exception {
        mvc.perform(post("/api/holdings").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol":"TCS","quantity":1,"buyPrice":10,"buyDate":"2026-11-03","horizon":"BTST"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("SWING or POSITIONAL")));
    }

    @Test
    void acceptsTodaysDateInIndiaEvenWhenItIsStillYesterdayInUtc() throws Exception {
        when(market.dailyCandles("TCS")).thenReturn(TestCandles.trend(400, 1000, 0.001, 1_000, LocalDate.of(2026, 11, 11)));
        when(repository.add(any())).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/api/holdings").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol":"TCS","quantity":1,"buyPrice":10,"buyDate":"2026-11-12","horizon":"SWING"}"""))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsABuyDateInTheFuture() throws Exception {
        mvc.perform(post("/api/holdings").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol":"TCS","quantity":1,"buyPrice":10,"buyDate":"2026-11-13","horizon":"SWING"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("buyDate can't be in the future"));
    }

    @Test
    void deletesAHolding() throws Exception {
        when(repository.delete(3L)).thenReturn(true);

        mvc.perform(delete("/api/holdings/3").header("Authorization", AUTH)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/holdings/4").header("Authorization", AUTH)).andExpect(status().isNotFound());
    }

    @Test
    void everyEndpointNeedsTheToken() throws Exception {
        mvc.perform(get("/api/holdings")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/holdings").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/holdings/1")).andExpect(status().isUnauthorized());
    }
}
