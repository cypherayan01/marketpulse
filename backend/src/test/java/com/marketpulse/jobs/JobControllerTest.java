package com.marketpulse.jobs;

import com.marketpulse.brief.PreMarketBriefService;
import com.marketpulse.btst.BtstScanService;
import com.marketpulse.config.MarketPulseProperties;
import com.marketpulse.news.NewsAlertService;
import com.marketpulse.news.NewsRunSummary;
import com.marketpulse.positional.PositionalScanService;
import com.marketpulse.swing.EodWatchlistService;
import com.marketpulse.tracking.SignalTracker;
import com.marketpulse.holdings.HoldingsCheckService;
import com.marketpulse.predictions.PredictionUpdater;
import com.marketpulse.predictions.ReplayService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobController.class)
@org.springframework.context.annotation.Import(BackgroundJobs.class)
@EnableConfigurationProperties(MarketPulseProperties.class)
@TestPropertySource(properties = "marketpulse.job-token=test-token")
class JobControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private NewsAlertService newsAlertService;

    @MockitoBean
    private EodWatchlistService eodWatchlistService;

    @MockitoBean
    private PreMarketBriefService preMarketBriefService;

    @MockitoBean
    private PositionalScanService positionalScanService;

    @MockitoBean
    private BtstScanService btstScanService;

    @MockitoBean
    private SignalTracker signalTracker;

    @MockitoBean
    private HoldingsCheckService holdingsCheckService;

    @MockitoBean
    private PredictionUpdater predictionUpdater;

    @MockitoBean
    private ReplayService replayService;

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mvc.perform(post("/jobs/news")).andExpect(status().isUnauthorized());

        verify(newsAlertService, never()).run();
    }

    @Test
    void rejectsRequestWithWrongToken() throws Exception {
        mvc.perform(post("/jobs/news").header("X-Job-Token", "nope")).andExpect(status().isUnauthorized());
    }

    @Test
    void runsNewsJobAndReturnsSummary() throws Exception {
        when(newsAlertService.run()).thenReturn(new NewsRunSummary(40, 12, 5, 1));

        mvc.perform(post("/jobs/news").header("X-Job-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fetched").value(40))
                .andExpect(jsonPath("$.relevant").value(5))
                .andExpect(jsonPath("$.alerted").value(1));
    }

    @Test
    void eodJobRequiresToken() throws Exception {
        mvc.perform(post("/jobs/eod")).andExpect(status().isUnauthorized());
    }

    @Test
    void eodJobIsAcceptedAndRunsInBackground() throws Exception {
        mvc.perform(post("/jobs/eod").header("X-Job-Token", "test-token"))
                .andExpect(status().isAccepted());

        verify(eodWatchlistService, timeout(10_000)).run(false);
        verify(signalTracker, timeout(10_000)).update(); // paper trades move forward after the close
        verify(holdingsCheckService, timeout(10_000)).run(false); // and your own holdings are checked
        verify(predictionUpdater, timeout(10_000)).update();     // and every prediction's checkpoints
    }

    @Test
    void replayJobRunsTheHistoryReplay() throws Exception {
        mvc.perform(post("/jobs/replay").header("X-Job-Token", "test-token"))
                .andExpect(status().isAccepted());

        verify(replayService, timeout(10_000)).run();
    }

    @Test
    void holdingsJobRunsTheHoldingsCheck() throws Exception {
        mvc.perform(post("/jobs/holdings").header("X-Job-Token", "test-token"))
                .andExpect(status().isAccepted());

        verify(holdingsCheckService, timeout(10_000)).run(false);
    }

    @Test
    void trackJobRunsTheTracker() throws Exception {
        mvc.perform(post("/jobs/track").header("X-Job-Token", "test-token"))
                .andExpect(status().isAccepted());

        verify(signalTracker, timeout(10_000)).update();
    }

    @Test
    void eodJobCanBeForcedOnHolidays() throws Exception {
        mvc.perform(post("/jobs/eod").param("force", "true").header("X-Job-Token", "test-token"))
                .andExpect(status().isAccepted());

        verify(eodWatchlistService, timeout(10_000)).run(true);
    }

    @Test
    void premarketJobRequiresToken() throws Exception {
        mvc.perform(post("/jobs/premarket")).andExpect(status().isUnauthorized());
    }

    @Test
    void premarketJobIsAcceptedAndRunsInBackground() throws Exception {
        mvc.perform(post("/jobs/premarket").header("X-Job-Token", "test-token"))
                .andExpect(status().isAccepted());

        verify(preMarketBriefService, timeout(10_000)).run(false);
    }

    @Test
    void positionalJobRequiresToken() throws Exception {
        mvc.perform(post("/jobs/positional")).andExpect(status().isUnauthorized());
    }

    @Test
    void positionalJobIsAcceptedAndRunsInBackground() throws Exception {
        mvc.perform(post("/jobs/positional").header("X-Job-Token", "test-token"))
                .andExpect(status().isAccepted());

        verify(positionalScanService, timeout(10_000)).run();
    }

    @Test
    void btstJobRequiresToken() throws Exception {
        mvc.perform(post("/jobs/btst")).andExpect(status().isUnauthorized());
    }

    @Test
    void btstJobIsAcceptedAndRunsInBackground() throws Exception {
        mvc.perform(post("/jobs/btst").header("X-Job-Token", "test-token"))
                .andExpect(status().isAccepted());

        verify(btstScanService, timeout(10_000)).run(false);
    }
}
