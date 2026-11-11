package com.marketpulse.dashboard;

import com.marketpulse.brief.PreMarketBriefService;
import com.marketpulse.btst.BtstScanService;
import com.marketpulse.config.MarketPulseProperties;
import com.marketpulse.events.EventCalendar;
import com.marketpulse.jobs.BackgroundJobs;
import com.marketpulse.jobs.Tokens;
import com.marketpulse.news.NewsAlertService;
import com.marketpulse.positional.PositionalScanService;
import com.marketpulse.reports.ReportStore;
import com.marketpulse.signals.TradeStyle;
import com.marketpulse.swing.EodWatchlistService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/** API for the React dashboard, protected by {@code Authorization: Bearer <DASHBOARD_TOKEN>}. */
@RestController
@RequestMapping("/api")
public class DashboardController {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int EVENT_DAYS = 30;

    private final ReportStore store;
    private final EventCalendar calendar;
    private final Clock clock;
    private final BackgroundJobs background;
    private final String dashboardToken;
    private final Map<String, Runnable> jobs;

    public DashboardController(ReportStore store, EventCalendar calendar, Clock clock, BackgroundJobs background,
                               MarketPulseProperties props, NewsAlertService news, PreMarketBriefService brief,
                               EodWatchlistService eod, PositionalScanService positional, BtstScanService btst) {
        this.store = store;
        this.calendar = calendar;
        this.clock = clock;
        this.background = background;
        this.dashboardToken = props.dashboard().token();
        // Started from the dashboard, jobs behave exactly like scheduled runs (and also notify Telegram/email).
        this.jobs = Map.of(
                "news", news::run,
                "premarket", () -> brief.run(true),
                "eod", () -> eod.run(false),
                "positional", positional::run,
                "btst", () -> btst.run(false));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardView> dashboard(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (!Tokens.matches(dashboardToken, Tokens.bearer(auth))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        LocalDate today = LocalDate.now(clock.withZone(IST));
        return ResponseEntity.ok(new DashboardView(
                clock.instant(),
                store.latestBrief().orElse(null),
                store.latestScan(TradeStyle.SWING).orElse(null),
                store.latestScan(TradeStyle.POSITIONAL).orElse(null),
                store.latestScan(TradeStyle.BTST).orElse(null),
                store.recentNews(),
                calendar.upcoming(today, EVENT_DAYS)));
    }

    @PostMapping("/run/{job}")
    public ResponseEntity<Void> run(@RequestHeader(value = "Authorization", required = false) String auth,
                                    @PathVariable String job) {
        if (!Tokens.matches(dashboardToken, Tokens.bearer(auth))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Runnable task = jobs.get(job);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        background.submit("Dashboard-triggered " + job, task);
        return ResponseEntity.accepted().build();
    }
}
