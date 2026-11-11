package com.marketpulse.reports;

import com.marketpulse.news.NewsImpact;
import com.marketpulse.signals.TradeStyle;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Latest output of every job, for the dashboard. In memory only: after a restart it fills up again as
 * scheduled jobs run (or when a job is started from the dashboard).
 */
public class ReportStore {

    private final int newsCapacity;
    private final Map<TradeStyle, ScanReport> scans = new EnumMap<>(TradeStyle.class);
    private final Deque<NewsEntry> news = new ArrayDeque<>();
    private BriefReport brief;

    public ReportStore(int newsCapacity) {
        this.newsCapacity = newsCapacity;
    }

    public synchronized void saveScan(ScanReport report) {
        scans.put(report.style(), report);
    }

    public synchronized Optional<ScanReport> latestScan(TradeStyle style) {
        return Optional.ofNullable(scans.get(style));
    }

    public synchronized void saveBrief(BriefReport report) {
        brief = report;
    }

    public synchronized Optional<BriefReport> latestBrief() {
        return Optional.ofNullable(brief);
    }

    public synchronized void addNews(NewsImpact impact, boolean alerted, Instant scoredAt) {
        if (news.stream().anyMatch(n -> n.id().equals(impact.item().id()))) {
            return;
        }
        news.addFirst(new NewsEntry(impact.item().id(), impact.item().title(), impact.item().source(),
                impact.item().link(), impact.item().publishedAt(), scoredAt, impact.impactScore(), impact.direction(),
                impact.sectors(), impact.stocks(), impact.reason(), alerted));
        while (news.size() > newsCapacity) {
            news.removeLast();
        }
    }

    /** Newest first. */
    public synchronized List<NewsEntry> recentNews() {
        return List.copyOf(news);
    }
}
