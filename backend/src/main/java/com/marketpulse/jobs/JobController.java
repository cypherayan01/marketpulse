package com.marketpulse.jobs;

import com.marketpulse.config.MarketPulseProperties;
import com.marketpulse.news.NewsAlertService;
import com.marketpulse.news.NewsRunSummary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Endpoints triggered by an external scheduler (GitHub Actions / cron-job.org).
 * On Render's free tier the app sleeps, so in-process @Scheduled jobs would not fire reliably.
 */
@RestController
@RequestMapping("/jobs")
public class JobController {

    private final NewsAlertService newsAlertService;
    private final byte[] jobToken;

    public JobController(NewsAlertService newsAlertService, MarketPulseProperties props) {
        this.newsAlertService = newsAlertService;
        this.jobToken = props.jobToken() == null ? new byte[0] : props.jobToken().getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/news")
    public ResponseEntity<NewsRunSummary> runNews(@RequestHeader(value = "X-Job-Token", required = false) String token) {
        if (!authorized(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(newsAlertService.run());
    }

    private boolean authorized(String token) {
        // Fail closed when no token is configured; constant-time comparison otherwise.
        return jobToken.length > 0 && token != null
                && MessageDigest.isEqual(jobToken, token.getBytes(StandardCharsets.UTF_8));
    }
}
