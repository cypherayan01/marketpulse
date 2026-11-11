package com.marketpulse.reports;

import java.util.List;
import java.util.Optional;

/** Durable copy of the {@link ReportStore}, so the dashboard isn't empty after a restart. */
public interface ReportArchive {

    void saveScan(ScanReport report);

    void saveBrief(BriefReport report);

    void saveNews(List<NewsEntry> news);

    void saveHoldings(HoldingsReport report);

    List<ScanReport> loadScans();

    Optional<BriefReport> loadBrief();

    List<NewsEntry> loadNews();

    Optional<HoldingsReport> loadHoldings();

    /** Keeps nothing - for tests and when no database is wanted. */
    ReportArchive NONE = new ReportArchive() {
        @Override public void saveScan(ScanReport report) { }
        @Override public void saveBrief(BriefReport report) { }
        @Override public void saveNews(List<NewsEntry> news) { }
        @Override public void saveHoldings(HoldingsReport report) { }
        @Override public Optional<HoldingsReport> loadHoldings() { return Optional.empty(); }
        @Override public List<ScanReport> loadScans() { return List.of(); }
        @Override public Optional<BriefReport> loadBrief() { return Optional.empty(); }
        @Override public List<NewsEntry> loadNews() { return List.of(); }
    };
}
