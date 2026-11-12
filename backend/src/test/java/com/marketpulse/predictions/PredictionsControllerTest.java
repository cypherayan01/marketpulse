package com.marketpulse.predictions;

import com.marketpulse.config.MarketPulseProperties;
import com.marketpulse.signals.Classification;
import com.marketpulse.signals.TradeStyle;
import com.marketpulse.universe.Segment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PredictionsController.class)
@EnableConfigurationProperties(MarketPulseProperties.class)
@TestPropertySource(properties = "marketpulse.dashboard.token=dash-token")
class PredictionsControllerTest {

    private static final String AUTH = "Bearer dash-token";
    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);

    @Autowired private MockMvc mvc;
    @MockitoBean private PredictionRepository repository;

    @Test
    void scorecardSummarisesLivePredictionsByDefault() throws Exception {
        when(repository.findAll(PredictionSource.LIVE)).thenReturn(List.of(
                prediction("TCS", DAY, Map.of(5, 3.0), Map.of(5, 1.0)),
                prediction("INFY", DAY.plusDays(3), Map.of(), Map.of())));

        mvc.perform(get("/api/predictions/scorecard").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("LIVE"))
                .andExpect(jsonPath("$.predictions").value(2))
                .andExpect(jsonPath("$.firstDate").value("2026-11-02"))
                .andExpect(jsonPath("$.lastDate").value("2026-11-05"))
                .andExpect(jsonPath("$.styles[0].style").value("SWING"))
                .andExpect(jsonPath("$.styles[0].checkpoints[3].sessions").value(5))
                .andExpect(jsonPath("$.styles[0].checkpoints[3].hitRatePct").value(100.0))
                .andExpect(jsonPath("$.caveat").doesNotExist());
    }

    @Test
    void replayScorecardCarriesItsCaveat() throws Exception {
        when(repository.findAll(PredictionSource.REPLAY)).thenReturn(List.of());

        mvc.perform(get("/api/predictions/scorecard").param("source", "REPLAY").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("REPLAY"))
                .andExpect(jsonPath("$.caveat").value(org.hamcrest.Matchers.containsString("today's index lists")));
    }

    @Test
    void listsWhatWasPredictedOnADay() throws Exception {
        when(repository.findByDate(PredictionSource.LIVE, DAY)).thenReturn(List.of(
                prediction("TCS", DAY, Map.of(1, 1.0, 5, 3.0), Map.of(1, 0.5, 5, 1.0))));

        mvc.perform(get("/api/predictions").param("date", "2026-11-02").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("TCS"))
                .andExpect(jsonPath("$[0].call").value("BUY"))
                .andExpect(jsonPath("$[0].returns.5").value(3.0))
                .andExpect(jsonPath("$[0].niftyReturns.5").value(1.0));
    }

    @Test
    void listsTheDaysThatHavePredictions() throws Exception {
        when(repository.findDates(PredictionSource.LIVE)).thenReturn(List.of(DAY.plusDays(1), DAY));

        mvc.perform(get("/api/predictions/dates").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("2026-11-03"));
    }

    @Test
    void everyEndpointNeedsTheToken() throws Exception {
        mvc.perform(get("/api/predictions/scorecard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/predictions").param("date", "2026-11-02")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/predictions/dates")).andExpect(status().isUnauthorized());
    }

    private static Prediction prediction(String symbol, LocalDate day, Map<Integer, Double> r, Map<Integer, Double> n) {
        return Prediction.of(PredictionSource.LIVE, TradeStyle.SWING, symbol, Segment.LARGE, day, 86,
                Classification.STRONG_BULLISH, Call.BUY, 100, 1000, 0, Instant.EPOCH).withId(1).withOutcomes(r, n, null, null);
    }
}
