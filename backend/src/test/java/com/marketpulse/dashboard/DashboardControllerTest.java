package com.marketpulse.dashboard;

import com.marketpulse.brief.PreMarketBriefService;
import com.marketpulse.btst.BtstScanService;
import com.marketpulse.config.MarketPulseProperties;
import com.marketpulse.events.EventCalendar;
import com.marketpulse.events.Impact;
import com.marketpulse.events.MarketEvent;
import com.marketpulse.jobs.BackgroundJobs;
import com.marketpulse.news.NewsAlertService;
import com.marketpulse.positional.PositionalScanService;
import com.marketpulse.reports.ReportStore;
import com.marketpulse.reports.ScanReport;
import com.marketpulse.signals.Classification;
import com.marketpulse.signals.Confidence;
import com.marketpulse.signals.TradePlan;
import com.marketpulse.signals.TradeSignal;
import com.marketpulse.signals.TradeStyle;
import com.marketpulse.swing.EodWatchlistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import({BackgroundJobs.class, WebConfig.class, DashboardControllerTest.Fixtures.class})
@EnableConfigurationProperties(MarketPulseProperties.class)
@TestPropertySource(properties = {
        "marketpulse.dashboard.token=dash-token",
        "marketpulse.dashboard.allowed-origins=https://marketpulse.vercel.app"})
class DashboardControllerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

    @TestConfiguration
    static class Fixtures {
        @Bean
        ReportStore reportStore() {
            ReportStore store = new ReportStore(100);
            TradeSignal upco = new TradeSignal("UPCO", TradeStyle.SWING, 86, Classification.STRONG_BULLISH,
                    Confidence.HIGH, List.of("Above 200-day EMA"), List.of("RSI 77 overbought"),
                    new TradePlan(110, 104, 122, 5.45), "Daily close below ₹104.00 (stop loss)", 110);
            store.saveScan(new ScanReport(TradeStyle.SWING, Instant.parse("2026-11-11T11:00:20Z"), TODAY,
                    "Nifty 25,255.05 (+0.84% today)", List.of(upco), List.of(), Map.of("UPCO", TODAY.plusDays(6)),
                    List.of(), 50, List.of(), List.of()));
            return store;
        }

        @Bean
        EventCalendar eventCalendar() {
            return new EventCalendar(List.of(MarketEvent.macro(TODAY.plusDays(3), "US CPI inflation data", Impact.HIGH)));
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"), ZoneId.of("UTC"));
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean private NewsAlertService news;
    @MockitoBean private PreMarketBriefService brief;
    @MockitoBean private EodWatchlistService eod;
    @MockitoBean private PositionalScanService positional;
    @MockitoBean private BtstScanService btst;

    @Test
    void rejectsRequestsWithoutTheDashboardToken() throws Exception {
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dashboard").header("Authorization", "Bearer wrong")).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsTheLatestReportsAsJson() throws Exception {
        mvc.perform(get("/api/dashboard").header("Authorization", "Bearer dash-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverTime").value("2026-11-11T12:00:00Z"))
                .andExpect(jsonPath("$.swing.session").value("2026-11-11"))
                .andExpect(jsonPath("$.swing.generatedAt").value("2026-11-11T11:00:20Z"))
                .andExpect(jsonPath("$.swing.bullish[0].symbol").value("UPCO"))
                .andExpect(jsonPath("$.swing.bullish[0].plan.stopLoss").value(104.0))
                .andExpect(jsonPath("$.swing.resultsSoon.UPCO").value("2026-11-17"))
                .andExpect(jsonPath("$.positional").doesNotExist())
                .andExpect(jsonPath("$.brief").doesNotExist())
                .andExpect(jsonPath("$.events[0].title").value("US CPI inflation data"));
    }

    @Test
    void startsAJobInTheBackground() throws Exception {
        mvc.perform(post("/api/run/eod").header("Authorization", "Bearer dash-token"))
                .andExpect(status().isAccepted());
        verify(eod, timeout(2000)).run(false);

        mvc.perform(post("/api/run/news").header("Authorization", "Bearer dash-token"))
                .andExpect(status().isAccepted());
        verify(news, timeout(2000)).run();
    }

    @Test
    void unknownJobIsNotFound() throws Exception {
        mvc.perform(post("/api/run/lottery").header("Authorization", "Bearer dash-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void runningAJobAlsoNeedsTheToken() throws Exception {
        mvc.perform(post("/api/run/eod")).andExpect(status().isUnauthorized());
    }

    @Test
    void allowsCrossOriginCallsOnlyFromTheDashboardSite() throws Exception {
        mvc.perform(options("/api/dashboard")
                        .header("Origin", "https://marketpulse.vercel.app")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://marketpulse.vercel.app"));

        mvc.perform(options("/api/dashboard")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
