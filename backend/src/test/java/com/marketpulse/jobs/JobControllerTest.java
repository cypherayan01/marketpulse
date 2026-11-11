package com.marketpulse.jobs;

import com.marketpulse.brief.PreMarketBriefService;
import com.marketpulse.btst.BtstScanService;
import com.marketpulse.config.MarketPulseProperties;
import com.marketpulse.news.NewsAlertService;
import com.marketpulse.news.NewsRunSummary;
import com.marketpulse.positional.PositionalScanService;
import com.marketpulse.swing.EodWatchlistService;
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

        verify(eodWatchlistService, timeout(2000)).run(false);
    }

    @Test
    void eodJobCanBeForcedOnHolidays() throws Exception {
        mvc.perform(post("/jobs/eod").param("force", "true").header("X-Job-Token", "test-token"))
                .andExpect(status().isAccepted());

        verify(eodWatchlistService, timeout(2000)).run(true);
    }

    @Test
    void premarketJobRequiresToken() throws Exception {
        mvc.perform(post("/jobs/premarket")).andExpect(status().isUnauthorized());
    }

    @Test
    void premarketJobIsAcceptedAndRunsInBackground() throws Exception {
        mvc.perform(post("/jobs/premarket").header("X-Job-Token", "test-token"))
                .andExpect(status().isAccepted());

        verify(preMarketBriefService, timeout(2000)).run(false);
    }

    @Test
    void positionalJobRequiresToken() throws Exception {
        mvc.perform(post("/jobs/positional")).andExpect(status().isUnauthorized());
    }

    @Test
    void positionalJobIsAcceptedAndRunsInBackground() throws Exception {
        mvc.perform(post("/jobs/positional").header("X-Job-Token", "test-token"))
                .andExpect(status().isAccepted());

        verify(positionalScanService, timeout(2000)).run();
    }

    @Test
    void btstJobRequiresToken() throws Exception {
        mvc.perform(post("/jobs/btst")).andExpect(status().isUnauthorized());
    }

    @Test
    void btstJobIsAcceptedAndRunsInBackground() throws Exception {
        mvc.perform(post("/jobs/btst").header("X-Job-Token", "test-token"))
                .andExpect(status().isAccepted());

        verify(btstScanService, timeout(2000)).run(false);
    }
}
