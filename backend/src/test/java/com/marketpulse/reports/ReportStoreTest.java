package com.marketpulse.reports;

import com.marketpulse.news.Direction;
import com.marketpulse.news.NewsImpact;
import com.marketpulse.news.NewsItem;
import com.marketpulse.signals.TradeStyle;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReportStoreTest {

    private static final Instant NOW = Instant.parse("2026-11-11T11:00:00Z");

    @Test
    void keepsTheLatestScanPerStyle() {
        ReportStore store = new ReportStore(100);
        store.saveScan(scan(TradeStyle.SWING, "first"));
        store.saveScan(scan(TradeStyle.SWING, "second"));
        store.saveScan(scan(TradeStyle.BTST, "btst"));

        assertThat(store.latestScan(TradeStyle.SWING)).map(ScanReport::market).contains("second");
        assertThat(store.latestScan(TradeStyle.BTST)).map(ScanReport::market).contains("btst");
        assertThat(store.latestScan(TradeStyle.POSITIONAL)).isEmpty();
    }

    @Test
    void keepsRecentNewsNewestFirstUpToCapacity() {
        ReportStore store = new ReportStore(2);
        store.addNews(impact("one"), false, NOW);
        store.addNews(impact("two"), true, NOW.plusSeconds(60));
        store.addNews(impact("three"), false, NOW.plusSeconds(120));

        List<NewsEntry> news = store.recentNews();

        assertThat(news).extracting(NewsEntry::title).containsExactly("three", "two");
        assertThat(news.get(1).alerted()).isTrue();
    }

    @Test
    void sameHeadlineIsNotStoredTwice() {
        ReportStore store = new ReportStore(10);
        store.addNews(impact("one"), false, NOW);
        store.addNews(impact("one"), true, NOW.plusSeconds(60));

        assertThat(store.recentNews()).hasSize(1);
    }

    @Test
    void restoresTheLastSavedReportsOnStartup() {
        RecordingArchive archive = new RecordingArchive();
        archive.scans.add(scan(TradeStyle.SWING, "from yesterday"));
        archive.news.add(new NewsEntry("old", "old headline", "ET", "https://x", NOW, NOW, 5, Direction.BULLISH,
                List.of(), List.of(), "why", false));

        ReportStore store = new ReportStore(100, archive);

        assertThat(store.latestScan(TradeStyle.SWING)).map(ScanReport::market).contains("from yesterday");
        assertThat(store.recentNews()).extracting(NewsEntry::title).containsExactly("old headline");
    }

    @Test
    void savesEveryReportToTheArchiveAndTellsListeners() {
        RecordingArchive archive = new RecordingArchive();
        ReportStore store = new ReportStore(100, archive);
        java.util.List<ScanReport> heard = new java.util.ArrayList<>();
        store.onScan(heard::add);

        store.saveScan(scan(TradeStyle.BTST, "btst"));
        store.addNews(impact("one"), false, NOW);

        assertThat(archive.scans).extracting(ScanReport::market).containsExactly("btst");
        assertThat(heard).extracting(ScanReport::market).containsExactly("btst");
        assertThat(archive.news).extracting(NewsEntry::title).containsExactly("one");
    }

    @Test
    void aFailingListenerDoesNotLoseTheReport() {
        ReportStore store = new ReportStore(100);
        store.onScan(r -> { throw new IllegalStateException("database down"); });

        store.saveScan(scan(TradeStyle.SWING, "kept"));

        assertThat(store.latestScan(TradeStyle.SWING)).isPresent();
    }

    private static class RecordingArchive implements ReportArchive {
        final java.util.List<ScanReport> scans = new java.util.ArrayList<>();
        final java.util.List<NewsEntry> news = new java.util.ArrayList<>();
        BriefReport brief;

        @Override public void saveScan(ScanReport report) { scans.add(report); }
        @Override public void saveBrief(BriefReport report) { brief = report; }
        @Override public void saveNews(java.util.List<NewsEntry> all) { news.clear(); news.addAll(all); }
        @Override public java.util.List<ScanReport> loadScans() { return scans; }
        @Override public java.util.Optional<BriefReport> loadBrief() { return java.util.Optional.ofNullable(brief); }
        @Override public java.util.List<NewsEntry> loadNews() { return news; }
    }

    private static ScanReport scan(TradeStyle style, String market) {
        return new ScanReport(style, NOW, LocalDate.of(2026, 11, 11), market, List.of(), List.of(), Map.of(),
                List.of(), 0, List.of(), List.of());
    }

    private static NewsImpact impact(String title) {
        return new NewsImpact(new NewsItem(title, title, "", "https://x/" + title, "Test", NOW), 5, Direction.BULLISH,
                List.of("IT"), List.of("TCS"), "why");
    }
}
