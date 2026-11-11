package com.marketpulse.news;

import com.marketpulse.alerts.AlertDispatcher;
import com.marketpulse.events.EventCalendar;
import com.marketpulse.events.MarketEvent;
import com.marketpulse.events.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One news-poll run: fetch → keep fresh & unseen → keyword filter → AI impact analysis → alert on high impact.
 * Results dates announced in headlines are recorded in the {@link EventCalendar}.
 */
public class NewsAlertService {

    private static final Logger log = LoggerFactory.getLogger(NewsAlertService.class);

    private final List<NewsSource> sources;
    private final NewsRelevanceFilter filter;
    private final SeenNewsRegistry seen;
    private final NewsImpactAnalyzer analyzer;
    private final AlertDispatcher dispatcher;
    private final EventCalendar calendar;
    private final Clock clock;
    private final NewsAlertSettings settings;

    public NewsAlertService(List<NewsSource> sources, NewsRelevanceFilter filter, SeenNewsRegistry seen,
                            NewsImpactAnalyzer analyzer, AlertDispatcher dispatcher, EventCalendar calendar,
                            Clock clock, NewsAlertSettings settings) {
        this.sources = sources;
        this.filter = filter;
        this.seen = seen;
        this.analyzer = analyzer;
        this.dispatcher = dispatcher;
        this.calendar = calendar;
        this.clock = clock;
        this.settings = settings;
    }

    public synchronized NewsRunSummary run() {
        List<NewsItem> fetched = fetchAll();

        Instant cutoff = clock.instant().minus(settings.lookback());
        Map<String, NewsItem> fresh = new LinkedHashMap<>();
        for (NewsItem item : fetched) {
            if (item.publishedAt().isAfter(cutoff) && !seen.hasSeen(item.id())) {
                fresh.putIfAbsent(item.id(), item);
            }
        }

        List<NewsItem> relevant = new ArrayList<>();
        for (NewsItem item : fresh.values()) {
            if (filter.isRelevant(item)) {
                relevant.add(item);
            } else {
                seen.markSeen(item.id()); // irrelevant: never look at it again
            }
        }

        int alerted = 0;
        for (List<NewsItem> batch : batches(relevant)) {
            alerted += analyzeAndAlert(batch);
        }

        NewsRunSummary summary = new NewsRunSummary(fetched.size(), fresh.size(), relevant.size(), alerted);
        log.info("News run finished: {}", summary);
        return summary;
    }

    private List<NewsItem> fetchAll() {
        List<NewsItem> all = new ArrayList<>();
        for (NewsSource source : sources) {
            try {
                all.addAll(source.fetchLatest());
            } catch (RuntimeException e) {
                log.warn("News source failed, skipping it this run: {}", e.getMessage());
            }
        }
        return all;
    }

    private int analyzeAndAlert(List<NewsItem> batch) {
        List<NewsImpact> impacts;
        try {
            impacts = analyzer.analyze(batch);
        } catch (RuntimeException e) {
            // Leave the batch unseen so the next run retries it (while it is still inside the lookback window).
            log.warn("Impact analysis failed for {} headlines, will retry next run: {}", batch.size(), e.getMessage());
            return 0;
        }
        batch.forEach(item -> seen.markSeen(item.id()));

        int alerted = 0;
        for (NewsImpact impact : impacts) {
            log.info("Scored {}/10 {} - {}", impact.impactScore(), impact.direction(), impact.item().title());
            if (impact.results() != null) {
                log.info("Results date from news: {} on {}", impact.results().symbol(), impact.results().date());
                calendar.add(MarketEvent.results(impact.results().date(), impact.results().symbol(), Source.NEWS));
            }
            if (impact.impactScore() >= settings.impactThreshold() && dispatcher.dispatch(impact)) {
                alerted++;
            }
        }
        return alerted;
    }

    private List<List<NewsItem>> batches(List<NewsItem> items) {
        List<List<NewsItem>> batches = new ArrayList<>();
        for (int i = 0; i < items.size(); i += settings.batchSize()) {
            batches.add(items.subList(i, Math.min(i + settings.batchSize(), items.size())));
        }
        return batches;
    }
}
