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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints triggered by an external scheduler (cron-job.org) with the X-Job-Token header.
 * On Render's free tier the app sleeps, so in-process @Scheduled jobs would not fire reliably.
 */
@RestController
@RequestMapping("/jobs")
public class JobController {

    private final NewsAlertService newsAlertService;
    private final EodWatchlistService eodWatchlistService;
    private final PreMarketBriefService preMarketBriefService;
    private final PositionalScanService positionalScanService;
    private final BtstScanService btstScanService;
    private final SignalTracker signalTracker;
    private final HoldingsCheckService holdingsCheck;
    private final BackgroundJobs background;
    private final String jobToken;

    public JobController(NewsAlertService newsAlertService, EodWatchlistService eodWatchlistService,
                         PreMarketBriefService preMarketBriefService, PositionalScanService positionalScanService,
                         BtstScanService btstScanService, SignalTracker signalTracker,
                         HoldingsCheckService holdingsCheck, BackgroundJobs background, MarketPulseProperties props) {
        this.newsAlertService = newsAlertService;
        this.eodWatchlistService = eodWatchlistService;
        this.preMarketBriefService = preMarketBriefService;
        this.positionalScanService = positionalScanService;
        this.btstScanService = btstScanService;
        this.signalTracker = signalTracker;
        this.holdingsCheck = holdingsCheck;
        this.background = background;
        this.jobToken = props.jobToken();
    }

    @PostMapping("/news")
    public ResponseEntity<NewsRunSummary> runNews(@RequestHeader(value = "X-Job-Token", required = false) String token) {
        if (!Tokens.matches(jobToken, token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(newsAlertService.run());
    }

    /**
     * After the close: the swing scan, paper-trade tracking, then your holdings check. Takes longer than
     * cron-job.org's timeout, so it runs in the background: 202 Accepted.
     */
    @PostMapping("/eod")
    public ResponseEntity<Void> runEod(@RequestHeader(value = "X-Job-Token", required = false) String token,
                                       @RequestParam(defaultValue = "false") boolean force) {
        return start(token, "EOD scan + tracking + holdings", () -> {
            eodWatchlistService.run(force);
            signalTracker.update();
            holdingsCheck.run(force);
        });
    }

    /** Holdings check on its own (it also runs after every /jobs/eod). */
    @PostMapping("/holdings")
    public ResponseEntity<Void> runHoldings(@RequestHeader(value = "X-Job-Token", required = false) String token,
                                            @RequestParam(defaultValue = "false") boolean force) {
        return start(token, "Holdings check", () -> holdingsCheck.run(force));
    }

    /** Paper-trade tracking on its own (it also runs after every /jobs/eod). */
    @PostMapping("/track")
    public ResponseEntity<Void> runTracking(@RequestHeader(value = "X-Job-Token", required = false) String token) {
        return start(token, "Signal tracking", signalTracker::update);
    }

    /** Fetches global cues and asks Claude to write the brief (~30-60 s). */
    @PostMapping("/premarket")
    public ResponseEntity<Void> runPremarket(@RequestHeader(value = "X-Job-Token", required = false) String token,
                                             @RequestParam(defaultValue = "false") boolean force) {
        return start(token, "Pre-market brief", () -> preMarketBriefService.run(force));
    }

    /** Weekly-chart scan for 1-6 month holds; meant for Sunday evening. */
    @PostMapping("/positional")
    public ResponseEntity<Void> runPositional(@RequestHeader(value = "X-Job-Token", required = false) String token) {
        return start(token, "Positional scan", positionalScanService::run);
    }

    /** Around 3:00 PM IST: stocks closing strong enough for an overnight (BTST) hold. */
    @PostMapping("/btst")
    public ResponseEntity<Void> runBtst(@RequestHeader(value = "X-Job-Token", required = false) String token,
                                        @RequestParam(defaultValue = "false") boolean force) {
        return start(token, "BTST scan", () -> btstScanService.run(force));
    }

    private ResponseEntity<Void> start(String token, String name, Runnable job) {
        if (!Tokens.matches(jobToken, token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        background.submit(name, job);
        return ResponseEntity.accepted().build();
    }
}
