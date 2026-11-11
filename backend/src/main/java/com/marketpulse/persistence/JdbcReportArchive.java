package com.marketpulse.persistence;

import com.marketpulse.reports.BriefReport;
import com.marketpulse.reports.HoldingsReport;
import com.marketpulse.reports.NewsEntry;
import com.marketpulse.reports.ReportArchive;
import com.marketpulse.reports.ScanReport;
import com.marketpulse.signals.TradeStyle;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Stores each report as a JSON snapshot (one row per kind, overwritten on every run). */
public class JdbcReportArchive implements ReportArchive {

    private static final String BRIEF = "BRIEF";
    private static final String NEWS = "NEWS";
    private static final String HOLDINGS = "HOLDINGS";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public JdbcReportArchive(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void saveScan(ScanReport report) {
        put(report.style().name(), json.writeValueAsString(report));
    }

    @Override
    public void saveBrief(BriefReport report) {
        put(BRIEF, json.writeValueAsString(report));
    }

    @Override
    public void saveNews(List<NewsEntry> news) {
        put(NEWS, json.writeValueAsString(news));
    }

    @Override
    public void saveHoldings(HoldingsReport report) {
        put(HOLDINGS, json.writeValueAsString(report));
    }

    @Override
    public Optional<HoldingsReport> loadHoldings() {
        return get(HOLDINGS).map(payload -> json.readValue(payload, HoldingsReport.class));
    }

    @Override
    public List<ScanReport> loadScans() {
        List<ScanReport> scans = new ArrayList<>();
        for (TradeStyle style : TradeStyle.values()) {
            get(style.name()).ifPresent(payload -> scans.add(json.readValue(payload, ScanReport.class)));
        }
        return scans;
    }

    @Override
    public Optional<BriefReport> loadBrief() {
        return get(BRIEF).map(payload -> json.readValue(payload, BriefReport.class));
    }

    @Override
    public List<NewsEntry> loadNews() {
        return get(NEWS).map(payload -> json.readValue(payload, new TypeReference<List<NewsEntry>>() { }))
                .orElse(List.of());
    }

    private synchronized void put(String kind, String payload) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int updated = jdbc.sql("UPDATE report_snapshot SET payload = :payload, saved_at = :now WHERE kind = :kind")
                .param("payload", payload).param("now", now).param("kind", kind).update();
        if (updated == 0) {
            jdbc.sql("INSERT INTO report_snapshot (kind, payload, saved_at) VALUES (:kind, :payload, :now)")
                    .param("kind", kind).param("payload", payload).param("now", now).update();
        }
    }

    private Optional<String> get(String kind) {
        return jdbc.sql("SELECT payload FROM report_snapshot WHERE kind = :kind")
                .param("kind", kind).query(String.class).optional();
    }
}
