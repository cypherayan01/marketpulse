package com.marketpulse.brief;

import com.marketpulse.alerts.AlertDispatcher;
import com.marketpulse.events.EventCalendar;
import com.marketpulse.events.MarketEvent;
import com.marketpulse.market.Candle;
import com.marketpulse.market.MarketDataClient;
import com.marketpulse.news.NewsItem;
import com.marketpulse.news.NewsRelevanceFilter;
import com.marketpulse.news.NewsSource;
import com.marketpulse.reports.BriefReport;
import com.marketpulse.reports.ReportStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 8:30 AM IST: global cues (computed here) + overnight headlines + today's events, written up by an LLM.
 * If the LLM fails, a numbers-only brief is still sent.
 */
public class PreMarketBriefService {

    private static final Logger log = LoggerFactory.getLogger(PreMarketBriefService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);

    private final MarketDataClient market;
    private final List<CueDefinition> cues;
    private final List<NewsSource> sources;
    private final NewsRelevanceFilter filter;
    private final EventCalendar calendar;
    private final BriefWriter writer;
    private final AlertDispatcher dispatcher;
    private final Clock clock;
    private final BriefSettings settings;
    private final ReportStore store;

    private final Object runLock = new Object();

    public PreMarketBriefService(MarketDataClient market, List<CueDefinition> cues, List<NewsSource> sources,
                                 NewsRelevanceFilter filter, EventCalendar calendar, BriefWriter writer,
                                 AlertDispatcher dispatcher, Clock clock, BriefSettings settings,
                                 ReportStore store) {
        this.market = market;
        this.cues = cues;
        this.sources = sources;
        this.filter = filter;
        this.calendar = calendar;
        this.writer = writer;
        this.dispatcher = dispatcher;
        this.clock = clock;
        this.settings = settings;
        this.store = store;
    }

    public BriefRunSummary run(boolean force) {
        synchronized (runLock) { // private lock: outside code can never hold it
            return runLocked(force);
        }
    }

    private BriefRunSummary runLocked(boolean force) {
        LocalDate today = LocalDate.now(clock.withZone(IST));
        if (!force && isWeekend(today)) {
            return BriefRunSummary.skipped("No brief on the weekend");
        }
        if (!force && settings.holidays().contains(today)) {
            return BriefRunSummary.skipped("NSE holiday - no brief");
        }

        List<CueReading> readings = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (CueDefinition cue : cues) {
            try {
                readings.add(read(cue));
            } catch (RuntimeException e) {
                log.warn("Cue {} unavailable: {}", cue.name(), e.getMessage());
                missing.add(cue.name());
            }
        }
        List<NewsItem> headlines = overnightHeadlines(today);
        List<MarketEvent> events = calendar.upcoming(today, settings.eventDays());
        BriefInput input = new BriefInput(today, readings, headlines, events, missing);

        String text;
        boolean aiWritten;
        try {
            text = writer.write(input);
            aiWritten = true;
        } catch (RuntimeException e) {
            log.warn("Brief writer failed, sending numbers only: {}", e.getMessage());
            text = "(AI summary unavailable this morning - numbers below.)";
            aiWritten = false;
        }
        store.saveBrief(new BriefReport(clock.instant(), today, text.strip(), aiWritten, readings, missing));
        String body = text.strip() + "\n\n" + BriefFormatter.cueTable(readings, missing) + BriefFormatter.footer();
        boolean sent = dispatcher.send(BriefFormatter.subject(today), body);

        BriefRunSummary summary = new BriefRunSummary(readings.size(), headlines.size(), events.size(), aiWritten, sent, null);
        log.info("Pre-market brief finished: {}", summary);
        return summary;
    }

    private CueReading read(CueDefinition cue) {
        List<Candle> candles = market.dailyCandles(cue.symbol());
        if (candles.size() < 2) {
            throw new IllegalStateException("not enough history");
        }
        Candle last = candles.getLast();
        Candle previous = candles.get(candles.size() - 2);
        return new CueReading(cue.name(), cue.symbol(), last.close(), (last.close() / previous.close() - 1) * 100,
                last.date());
    }

    /** Relevant headlines published after the previous trading session closed, newest first. */
    private List<NewsItem> overnightHeadlines(LocalDate today) {
        Instant since = previousSession(today).atTime(MARKET_CLOSE).atZone(IST).toInstant();
        Map<String, NewsItem> unique = new LinkedHashMap<>();
        for (NewsSource source : sources) {
            try {
                for (NewsItem item : source.fetchLatest()) {
                    if (item.publishedAt().isAfter(since) && filter.isRelevant(item)) {
                        unique.putIfAbsent(item.id(), item);
                    }
                }
            } catch (RuntimeException e) {
                log.warn("News source failed for brief: {}", e.getMessage());
            }
        }
        return unique.values().stream()
                .sorted(Comparator.comparing(NewsItem::publishedAt).reversed())
                .limit(settings.maxHeadlines())
                .toList();
    }

    private LocalDate previousSession(LocalDate today) {
        LocalDate day = today.minusDays(1);
        while (isWeekend(day) || settings.holidays().contains(day)) {
            day = day.minusDays(1);
        }
        return day;
    }

    private static boolean isWeekend(LocalDate day) {
        return day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
}
