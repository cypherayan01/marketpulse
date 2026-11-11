package com.marketpulse.positional;

import com.marketpulse.alerts.AlertDispatcher;
import com.marketpulse.events.EventCalendar;
import com.marketpulse.events.Impact;
import com.marketpulse.events.MarketEvent;
import com.marketpulse.events.Source;
import com.marketpulse.market.Candle;
import com.marketpulse.market.MarketDataClient;
import com.marketpulse.market.TestCandles;
import com.marketpulse.reports.ReportStore;
import com.marketpulse.signals.TradeStyle;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PositionalScanServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDate FRIDAY = LocalDate.of(2026, 11, 13);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 11, 15);
    private final Clock clock = Clock.fixed(SUNDAY.atTime(18, 0).atZone(IST).toInstant(), IST);

    private final Map<String, List<Candle>> data = new HashMap<>();
    private final MarketDataClient market = symbol -> {
        List<Candle> c = data.get(symbol);
        if (c == null) throw new IllegalStateException("no data for " + symbol);
        return c;
    };
    private final List<String> subjects = new ArrayList<>();
    private final List<String> bodies = new ArrayList<>();
    private final AlertDispatcher dispatcher = new AlertDispatcher(List.of((s, b) -> { subjects.add(s); bodies.add(b); }));
    private EventCalendar calendar = new EventCalendar(List.of());
    private final ReportStore store = new ReportStore(100);

    @Test
    void ranksLongTermSetupsAndStocksToAvoid() {
        data.put("^NSEI", TestCandles.trend(600, 20000, 0.0003, 0, FRIDAY));
        data.put("LEADER", TestCandles.trend(600, 100, 0.002, 1_000, FRIDAY));
        data.put("LAGGARD", TestCandles.trend(600, 100, -0.002, 1_000, FRIDAY));

        PositionalRunSummary summary = service(List.of("LEADER", "LAGGARD"), 5).run();

        assertThat(summary).isEqualTo(new PositionalRunSummary(2, 0, 1, 1, true));
        assertThat(subjects.getFirst()).contains("Positional outlook").contains("week ending 13 Nov 2026");
        String body = bodies.getFirst();
        assertThat(body).contains("hold ~1-6 months").contains("Stop").contains("Target");
        assertThat(body.indexOf("LEADER")).isLessThan(body.indexOf("LAGGARD"));
        assertThat(body).contains("Not investment advice");
    }

    @Test
    void failingSymbolIsReportedAndScanContinues() {
        data.put("^NSEI", TestCandles.trend(600, 20000, 0.0003, 0, FRIDAY));
        data.put("LEADER", TestCandles.trend(600, 100, 0.002, 1_000, FRIDAY));

        PositionalRunSummary summary = service(List.of("GHOST", "LEADER"), 5).run();

        assertThat(summary.scanned()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(bodies.getFirst()).contains("Data unavailable: GHOST");
    }

    @Test
    void suggestsWaitingWhenResultsAreDueSoon() {
        data.put("^NSEI", TestCandles.trend(600, 20000, 0.0003, 0, FRIDAY));
        data.put("LEADER", TestCandles.trend(600, 100, 0.002, 1_000, FRIDAY));
        calendar = new EventCalendar(List.of(MarketEvent.results(SUNDAY.plusDays(10), "LEADER", Source.CONFIG)));

        service(List.of("LEADER"), 5).run();

        assertThat(bodies.getFirst()).contains("Results on 25 Nov (in 10 days)");
    }

    @Test
    void listsNextWeeksEvents() {
        data.put("^NSEI", TestCandles.trend(600, 20000, 0.0003, 0, FRIDAY));
        data.put("LEADER", TestCandles.trend(600, 100, 0.002, 1_000, FRIDAY));
        calendar = new EventCalendar(List.of(
                MarketEvent.macro(SUNDAY.plusDays(4), "RBI policy decision", Impact.HIGH),
                MarketEvent.macro(SUNDAY.plusDays(30), "Much later", Impact.HIGH)));

        service(List.of("LEADER"), 5).run();

        assertThat(bodies.getFirst()).contains("RBI policy decision").doesNotContain("Much later");
    }

    @Test
    void savesTheReportForTheDashboard() {
        data.put("^NSEI", TestCandles.trend(600, 20000, 0.0003, 0, FRIDAY));
        data.put("LEADER", TestCandles.trend(600, 100, 0.002, 1_000, FRIDAY));

        service(List.of("LEADER"), 5).run();

        var report = store.latestScan(TradeStyle.POSITIONAL).orElseThrow();
        assertThat(report.session()).isEqualTo(FRIDAY);
        assertThat(report.bullish()).extracting(s -> s.symbol()).containsExactly("LEADER");
        assertThat(report.market()).contains("40-week");
    }

    @Test
    void indexUnavailableMeansNoReport() {
        PositionalRunSummary summary = service(List.of("LEADER"), 5).run();

        assertThat(summary.sent()).isFalse();
        assertThat(subjects).isEmpty();
    }

    private PositionalScanService service(List<String> universe, int topN) {
        return new PositionalScanService(market, new PositionalCalculator(), new PositionalScorer(), dispatcher,
                calendar, clock, new PositionalSettings(universe, "^NSEI", topN, 14), store);
    }
}
