package com.marketpulse.jobs;

import com.marketpulse.config.MarketPulseProperties;
import com.marketpulse.news.NewsAlertService;
import com.marketpulse.news.NewsRunSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobController.class)
@EnableConfigurationProperties(MarketPulseProperties.class)
@TestPropertySource(properties = "marketpulse.job-token=test-token")
class JobControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private NewsAlertService newsAlertService;

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
}
