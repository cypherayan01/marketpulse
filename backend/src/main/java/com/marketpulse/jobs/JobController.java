package com.marketpulse.jobs;

import com.marketpulse.brief.PreMarketBriefService;
import com.marketpulse.btst.BtstScanService;
import com.marketpulse.config.MarketPulseProperties;
import com.marketpulse.news.NewsAlertService;
import com.marketpulse.news.NewsRunSummary;
import com.marketpulse.positional.PositionalScanService;
import com.marketpulse.swing.EodWatchlistService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Endpoints triggered by an external scheduler (cron-job.org).
 * On Render's free tier the app sleeps, so in-process @Scheduled jobs would not fire reliably.
 */
@RestController
@RequestMapping("/jobs")
public class JobController {

    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    private final NewsAlertService newsAlertService;
    private final EodWatchlistService eodWatchlistService;
    private final PreMarketBriefService preMarketBriefService;
    private final PositionalScanService positionalScanService;
    private final BtstScanService btstScanService;
    private final byte[] jobToken;
    /** Long scans run here so the scheduler's HTTP call returns immediately; one at a time. */
    private final ExecutorService background = Executors.newSingleThreadExecutor();

    public JobController(NewsAlertService newsAlertService, EodWatchlistService eodWatchlistService,
                         PreMarketBriefService preMarketBriefService, PositionalScanService positionalScanService,
                         BtstScanService btstScanService, MarketPulseProperties props) {
        this.newsAlertService = newsAlertService;
        this.eodWatchlistService = eodWatchlistService;
        this.preMarketBriefService = preMarketBriefService;
        this.positionalScanService = positionalScanService;
        this.btstScanService = btstScanService;
        this.jobToken = props.jobToken() == null ? new byte[0] : props.jobToken().getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/news")
    public ResponseEntity<NewsRunSummary> runNews(@RequestHeader(value = "X-Job-Token", required = false) String token) {
        if (!authorized(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(newsAlertService.run());
    }

    /** Scans ~50 stocks (takes longer than cron-job.org's timeout), so it runs in the background: 202 Accepted. */
    @PostMapping("/eod")
    public ResponseEntity<Void> runEod(@RequestHeader(value = "X-Job-Token", required = false) String token,
                                       @RequestParam(defaultValue = "false") boolean force) {
        if (!authorized(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return runInBackground("EOD scan", () -> eodWatchlistService.run(force));
    }

    /** Fetches global cues and asks Claude to write the brief (can take ~30-60 s), so it runs in the background. */
    @PostMapping("/premarket")
    public ResponseEntity<Void> runPremarket(@RequestHeader(value = "X-Job-Token", required = false) String token,
                                             @RequestParam(defaultValue = "false") boolean force) {
        if (!authorized(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return runInBackground("Pre-market brief", () -> preMarketBriefService.run(force));
    }

    /** Weekly-chart scan for 1-6 month holds; meant for Sunday evening. */
    @PostMapping("/positional")
    public ResponseEntity<Void> runPositional(@RequestHeader(value = "X-Job-Token", required = false) String token) {
        if (!authorized(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return runInBackground("Positional scan", positionalScanService::run);
    }

    /** Around 3:00 PM IST: stocks closing strong enough for an overnight (BTST) hold. */
    @PostMapping("/btst")
    public ResponseEntity<Void> runBtst(@RequestHeader(value = "X-Job-Token", required = false) String token,
                                        @RequestParam(defaultValue = "false") boolean force) {
        if (!authorized(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return runInBackground("BTST scan", () -> btstScanService.run(force));
    }

    private ResponseEntity<Void> runInBackground(String name, Runnable job) {
        background.submit(() -> {
            try {
                job.run();
            } catch (RuntimeException e) {
                log.error("{} failed", name, e);
            }
        });
        return ResponseEntity.accepted().build();
    }

    @PreDestroy
    void shutdown() {
        background.shutdown();
    }

    private boolean authorized(String token) {
        // Fail closed when no token is configured; constant-time comparison otherwise.
        return jobToken.length > 0 && token != null
                && MessageDigest.isEqual(jobToken, token.getBytes(StandardCharsets.UTF_8));
    }
}
