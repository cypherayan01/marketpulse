package com.marketpulse.analysis;

import com.marketpulse.config.MarketPulseProperties;
import com.marketpulse.holdings.HoldingCheck;
import com.marketpulse.signals.TradeStyle;
import com.marketpulse.universe.IndexMember;
import com.marketpulse.universe.Segment;
import com.marketpulse.universe.Universe;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalysisController.class)
@Import(AnalysisControllerTest.Fixtures.class)
@EnableConfigurationProperties(MarketPulseProperties.class)
@TestPropertySource(properties = "marketpulse.dashboard.token=dash-token")
class AnalysisControllerTest {

    private static final String AUTH = "Bearer dash-token";
    private static final LocalDate DAY = LocalDate.of(2026, 11, 12);

    @TestConfiguration
    static class Fixtures {
        @Bean
        Universe universe() {
            return Universe.of(List.of(new IndexMember("TCS", "Tata Consultancy Services Ltd.", "Information Technology")),
                    List.of(), List.of(), List.of(new IndexMember("TCS", "Tata Consultancy Services Ltd.", "Information Technology")));
        }
    }

    @Autowired private MockMvc mvc;
    @MockitoBean private StockAnalyzer analyzer;
    @MockitoBean private StockExplainer explainer;

    @Test
    void analysesAStock() throws Exception {
        when(analyzer.analyze(eq("TCS"), eq(Optional.empty()))).thenReturn(analysis(null));

        mvc.perform(get("/api/analyze/TCS").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("BUY"))
                .andExpect(jsonPath("$.segment").value("LARGE"))
                .andExpect(jsonPath("$.priceDate").value("2026-11-12"))
                .andExpect(jsonPath("$.holding").doesNotExist());
    }

    @Test
    void passesYourPositionWhenYouOwnTheStock() throws Exception {
        when(analyzer.analyze(eq("TCS"), any())).thenReturn(analysis(null));

        mvc.perform(get("/api/analyze/tcs").header("Authorization", AUTH)
                        .param("buyPrice", "3100").param("buyDate", "2026-10-01").param("quantity", "5")
                        .param("horizon", "POSITIONAL").param("stopLoss", "2950"))
                .andExpect(status().isOk());

        verify(analyzer).analyze("TCS", Optional.of(
                new OwnedPosition(5, 3100, LocalDate.of(2026, 10, 1), TradeStyle.POSITIONAL, 2950.0)));
    }

    @Test
    void unknownStockIsNotFound() throws Exception {
        when(analyzer.analyze(eq("NOSUCH"), any())).thenThrow(new UnknownStockException("NOSUCH"));

        mvc.perform(get("/api/analyze/NOSUCH").header("Authorization", AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("No NSE price data found for NOSUCH - check the symbol"));
    }

    @Test
    void rejectsSomethingThatIsNotASymbol() throws Exception {
        mvc.perform(get("/api/analyze/{s}", "DROP TABLE").header("Authorization", AUTH))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownedPositionNeedsABuyDate() throws Exception {
        mvc.perform(get("/api/analyze/TCS").header("Authorization", AUTH).param("buyPrice", "3100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("buyDate is needed with buyPrice"));
    }

    @Test
    void listsTheKnownStocksForAutocomplete() throws Exception {
        mvc.perform(get("/api/universe").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("TCS"))
                .andExpect(jsonPath("$[0].name").value("Tata Consultancy Services Ltd."))
                .andExpect(jsonPath("$[0].segment").value("LARGE"));
    }

    @Test
    void explainsWithAi() throws Exception {
        when(analyzer.analyze(eq("TCS"), any())).thenReturn(analysis(null));
        when(explainer.explain(any())).thenReturn("TCS looks strong on both horizons...");

        mvc.perform(post("/api/analyze/TCS/explain").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("TCS looks strong on both horizons..."));
    }

    @Test
    void everyEndpointNeedsTheToken() throws Exception {
        mvc.perform(get("/api/analyze/TCS")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/universe")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/analyze/TCS/explain")).andExpect(status().isUnauthorized());
    }

    private static StockAnalysis analysis(HoldingCheck holding) {
        return new StockAnalysis("TCS", "Tata Consultancy Services Ltd.", "Information Technology", Segment.LARGE,
                3300, DAY, 1.2, AnalysisVerdict.BUY, "Buy - short-term and long-term trends agree", List.of("why"),
                null, null, null, holding, null, List.of(), Instant.parse("2026-11-12T11:30:00Z"));
    }
}
