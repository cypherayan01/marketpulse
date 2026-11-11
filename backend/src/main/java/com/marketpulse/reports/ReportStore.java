package com.marketpulse.reports;

import com.marketpulse.news.NewsImpact;
import com.marketpulse.signals.TradeStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Latest output of every job, for the dashboard. Kept in memory for fast reads, copied to a
 * {@link ReportArchive} so it survives restarts, and announced to listeners (e.g. paper-trade recording).
 * A failing archive or listener is logged and never loses the in-memory report.
 */
public class ReportStore {

    private static final Logger log = LoggerFactory.getLogger(ReportStore.class);

    private final int newsCapacity;
    private final ReportArchive archive;
    private final Map<TradeStyle, ScanReport> scans = new EnumMap<>(TradeStyle.class);
    private final Deque<NewsEntry> news = new ArrayDeque<>();
    private final List<Consumer<ScanReport>> scanListeners = new ArrayList<>();
    private BriefReport brief;

    public ReportStore(int newsCapacity) {
        this(newsCapacity, ReportArchive.NONE);
    }

    public ReportStore(int newsCapacity, ReportArchive archive) {
        this.newsCapacity = newsCapacity;
        this.archive = archive;
        try {
            archive.loadScans().forEach(s -> scans.put(s.style(), s));
            brief = archive.loadBrief().orElse(null);
            archive.loadNews().stream().limit(newsCapacity).forEach(news::addLast);
        } catch (RuntimeException e) {
            log.warn("Could not restore saved reports, starting empty: {}", e.getMessage());
        }
    }

    /** Called after every saved scan (on the job's thread). */
    public synchronized void onScan(Consumer<ScanReport> listener) {
        scanListeners.add(listener);
    }

    public synchronized void saveScan(ScanReport report) {
        scans.put(report.style(), report);
        safely("archive scan", () -> archive.saveScan(report));
        for (Consumer<ScanReport> listener : scanListeners) {
            safely("scan listener", () -> listener.accept(report));
        }
    }

    public synchronized Optional<ScanReport> latestScan(TradeStyle style) {
        return Optional.ofNullable(scans.get(style));
    }

    public synchronized void saveBrief(BriefReport report) {
        brief = report;
        safely("archive brief", () -> archive.saveBrief(report));
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
        List<NewsEntry> snapshot = List.copyOf(news);
        safely("archive news", () -> archive.saveNews(snapshot));
    }

    /** Newest first. */
    public synchronized List<NewsEntry> recentNews() {
        return List.copyOf(news);
    }

    private static void safely(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("{} failed: {}", what, e.getMessage());
        }
    }
}
