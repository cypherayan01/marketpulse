package com.marketpulse.persistence;

import com.marketpulse.brief.CueReading;
import com.marketpulse.events.Impact;
import com.marketpulse.events.MarketEvent;
import com.marketpulse.news.Direction;
import com.marketpulse.reports.BriefReport;
import com.marketpulse.reports.NewsEntry;
import com.marketpulse.reports.ScanReport;
import com.marketpulse.signals.Classification;
import com.marketpulse.signals.Confidence;
import com.marketpulse.signals.TradePlan;
import com.marketpulse.signals.TradeSignal;
import com.marketpulse.signals.TradeStyle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
class JdbcReportArchiveTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 11);

    @Autowired
    private JdbcClient jdbc;

    private JdbcReportArchive archive;

    @BeforeEach
    void setUp() {
        archive = new JdbcReportArchive(jdbc, JsonMapper.builder().build());
    }

    @Test
    void scansSurviveARoundTrip() {
        ScanReport swing = new ScanReport(TradeStyle.SWING, Instant.parse("2026-11-11T11:00:20Z"), DAY, "Nifty up",
                List.of(new TradeSignal("HDFCBANK", TradeStyle.SWING, 86, Classification.STRONG_BULLISH, Confidence.HIGH,
                        List.of("why"), List.of("watch"), new TradePlan(806.6, 771.47, 876.86, 4.36), "invalid if", 806.6)),
                List.of(), Map.of("HDFCBANK", DAY.plusDays(6)),
                List.of(MarketEvent.macro(DAY.plusDays(3), "US CPI", Impact.HIGH)), 50, List.of(), List.of("note"));

        archive.saveScan(swing);
        archive.saveScan(swing); // overwrite, not duplicate

        assertThat(archive.loadScans()).containsExactly(swing);
    }

    @Test
    void briefAndNewsSurviveARoundTrip() {
        BriefReport brief = new BriefReport(Instant.parse("2026-11-11T03:00:00Z"), DAY, "Mood: mixed", true,
                List.of(new CueReading("S&P 500", "^GSPC", 7605.63, 1.13, DAY.minusDays(1))), List.of("Gold"));
        List<NewsEntry> news = List.of(new NewsEntry("1", "RBI cuts", "ET", "https://x", Instant.parse("2026-11-11T05:00:00Z"),
                Instant.parse("2026-11-11T05:05:00Z"), 9, Direction.BULLISH, List.of("Banking"), List.of(), "why", true));

        archive.saveBrief(brief);
        archive.saveNews(news);

        assertThat(archive.loadBrief()).contains(brief);
        assertThat(archive.loadNews()).isEqualTo(news);
    }

    @Test
    void emptyArchiveLoadsNothing() {
        assertThat(archive.loadScans()).isEmpty();
        assertThat(archive.loadBrief()).isEmpty();
        assertThat(archive.loadNews()).isEmpty();
    }
}
