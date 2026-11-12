package com.marketpulse.swing;

import com.marketpulse.alerts.AlertDispatcher;
import com.marketpulse.alerts.Notifier;
import com.marketpulse.events.EventCalendar;
import com.marketpulse.events.Impact;
import com.marketpulse.events.MarketEvent;
import com.marketpulse.events.Source;
import com.marketpulse.market.Candle;
import com.marketpulse.market.MarketDataClient;
import com.marketpulse.market.TestCandles;
import com.marketpulse.reports.ReportStore;
import com.marketpulse.signals.TradeStyle;
import com.marketpulse.universe.IndexMember;
import com.marketpulse.universe.Segment;
import com.marketpulse.universe.Universe;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EodWatchlistServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);
    // 16:30 IST on TODAY
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-11T11:00:00Z"), ZoneId.of("UTC"));

    private final FakeMarketData market = new FakeMarketData();
    private EventCalendar calendar = new EventCalendar(List.of());
    private final ReportStore store = new ReportStore(100);
    private final List<String> subjects = new ArrayList<>();
    private final List<String> bodies = new ArrayList<>();
    private final Notifier notifier = (subject, body) -> { subjects.add(subject); bodies.add(body); };

    @Test
    void ranksBullishSetupsAndListsStocksToAvoid() {
        market.put("^NSEI", TestCandles.trend(400, 20000, 0.0005, 0, TODAY));
        market.put("UPCO", TestCandles.trend(400, 100, 0.004, 1_000, TODAY));
        market.put("DOWNCO", TestCandles.trend(400, 100, -0.004, 1_000, TODAY));

        EodRunSummary summary = service(List.of("UPCO", "DOWNCO"), 5).run(false);

        assertThat(summary).isEqualTo(new EodRunSummary(2, 0, 1, 1, true, null));
        assertThat(subjects.getFirst()).contains("Swing watchlist").contains("11 Nov");
        String body = bodies.getFirst();
        assertThat(body).contains("UPCO").contains("Stop").contains("Target");
        assertThat(body.indexOf("UPCO")).isLessThan(body.indexOf("DOWNCO"));
        assertThat(body).contains("Not investment advice");
    }

    @Test
    void savesTheReportForTheDashboard() {
        market.put("^NSEI", TestCandles.trend(400, 20000, 0.0005, 0, TODAY));
        market.put("UPCO", TestCandles.trend(400, 100, 0.004, 1_000, TODAY));
        market.put("DOWNCO", TestCandles.trend(400, 100, -0.004, 1_000, TODAY));
        calendar = new EventCalendar(List.of(MarketEvent.results(TODAY.plusDays(6), "UPCO", Source.CONFIG)));

        service(List.of("UPCO", "DOWNCO"), 5).run(false);

        var report = store.latestScan(TradeStyle.SWING).orElseThrow();
        assertThat(report.session()).isEqualTo(TODAY);
        assertThat(report.market()).startsWith("Nifty");
        assertThat(report.bullish()).extracting(s -> s.symbol()).containsExactly("UPCO");
        assertThat(report.bearish()).extracting(s -> s.symbol()).containsExactly("DOWNCO");
        assertThat(report.resultsSoon()).containsEntry("UPCO", TODAY.plusDays(6));
        assertThat(report.scanned()).isEqualTo(2);
    }

    @Test
    void reportsEachMarketCapSegmentSeparately() {
        market.put("^NSEI", TestCandles.trend(400, 20000, 0.0005, 0, TODAY));
        market.put("^CNX100", TestCandles.trend(400, 26000, 0.001, 0, TODAY));      // Nifty 100 index
        market.put("BIGCO", TestCandles.trend(400, 100, 0.004, 1_000, TODAY));
        market.put("TINYCO", TestCandles.trend(400, 100, 0.005, 1_000, TODAY));
        market.put("SLIDER", TestCandles.trend(400, 100, -0.004, 1_000, TODAY));
        Universe universe = Universe.of(List.of(member("BIGCO")), List.of(), List.of(member("TINYCO"), member("SLIDER")),
                List.of(member("BIGCO"), member("TINYCO"), member("SLIDER")));

        EodRunSummary summary = new EodWatchlistService(market, new IndicatorCalculator(), new SwingScorer(),
                new AlertDispatcher(List.of(notifier)), calendar, clock,
                new SwingSettings(universe, "^NSEI", 6, 15), store).run(false);

        String body = bodies.getFirst();
        assertThat(body).contains("🔵 LARGE CAP · Nifty 100 +0.10% today · 1 scanned")
                .contains("🟠 SMALL CAP · Nifty Smallcap 100 · 2 scanned");
        assertThat(body.indexOf("BIGCO")).isLessThan(body.indexOf("SMALL CAP"));
        assertThat(body.indexOf("SMALL CAP")).isLessThan(body.indexOf("TINYCO"));
        assertThat(summary.bullish()).isEqualTo(2);
        assertThat(summary.bearish()).isEqualTo(1);

        var report = store.latestScan(TradeStyle.SWING).orElseThrow();
        assertThat(report.sections()).extracting(s -> s.segment()).containsExactly(Segment.LARGE, Segment.SMALL);
        assertThat(report.sections().get(1).bearish()).extracting(s -> s.symbol()).containsExactly("SLIDER");
        assertThat(report.bullish()).extracting(s -> s.symbol()).containsExactlyInAnyOrder("TINYCO", "BIGCO"); // best overall
    }

    @Test
    void summarisesManyStocksWithoutDataInOneLine() {
        market.put("^NSEI", TestCandles.trend(400, 20000, 0.0005, 0, TODAY));
        market.put("UPCO", TestCandles.trend(400, 100, 0.004, 1_000, TODAY));
        List<String> universe = new java.util.ArrayList<>(List.of("UPCO"));
        for (int i = 0; i < 8; i++) universe.add("NEWIPO" + i);

        service(universe, 5).run(false);

        assertThat(bodies.getFirst()).contains("8 skipped (new listings or no data)").doesNotContain("NEWIPO3");
    }

    @Test
    void warnsClearlyWhenMuchOfTheDataIsMissing() {
        market.put("^NSEI", TestCandles.trend(400, 20000, 0.0005, 0, TODAY));
        market.put("UPCO", TestCandles.trend(400, 100, 0.004, 1_000, TODAY));
        market.put("DOWNCO", TestCandles.trend(400, 100, -0.004, 1_000, TODAY));
        // LOST1, LOST2 and OK3 have no data (e.g. a network drop) - 3 of 5 missing

        service(List.of("UPCO", "DOWNCO", "LOST1", "LOST2", "OK3"), 5).run(false);

        assertThat(bodies.getFirst()).contains("⚠️ Data incomplete: 3 of 5 stocks could not be fetched");
        assertThat(subjects.getFirst()).contains("(incomplete)");
    }

    @Test
    void aFewMissingNewListingsAreNotAnIncompleteScan() {
        market.put("^NSEI", TestCandles.trend(400, 20000, 0.0005, 0, TODAY));
        List<String> universe = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            market.put("CO" + i, TestCandles.trend(400, 100, 0.001 * (i % 5), 1_000, TODAY));
            universe.add("CO" + i);
        }
        universe.add("NEWIPO"); // 1 of 21 missing - under 10%

        service(universe, 5).run(false);

        assertThat(bodies.getFirst()).doesNotContain("Data incomplete");
        assertThat(subjects.getFirst()).doesNotContain("incomplete");
    }

    @Test
    void skipsWhenMarketWasClosedToday() {
        market.put("^NSEI", TestCandles.trend(400, 20000, 0.0005, 0, TODAY.minusDays(1))); // no candle today

        EodRunSummary summary = service(List.of("UPCO"), 5).run(false);

        assertThat(summary.sent()).isFalse();
        assertThat(summary.skippedReason()).contains("closed");
        assertThat(subjects).isEmpty();
    }

    @Test
    void forceRunsEvenWhenMarketWasClosed() {
        market.put("^NSEI", TestCandles.trend(400, 20000, 0.0005, 0, TODAY.minusDays(1)));
        market.put("UPCO", TestCandles.trend(400, 100, 0.004, 1_000, TODAY.minusDays(1)));

        assertThat(service(List.of("UPCO"), 5).run(true).sent()).isTrue();
    }

    @Test
    void failingSymbolIsCountedAndDoesNotStopTheScan() {
        market.put("^NSEI", TestCandles.trend(400, 20000, 0.0005, 0, TODAY));
        market.put("UPCO", TestCandles.trend(400, 100, 0.004, 1_000, TODAY));
        // "GHOST" has no data -> client throws

        EodRunSummary summary = service(List.of("GHOST", "UPCO"), 5).run(false);

        assertThat(summary.scanned()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(bodies.getFirst()).contains("UPCO").contains("GHOST");
    }

    @Test
    void limitsEachListToTopN() {
        market.put("^NSEI", TestCandles.trend(400, 20000, 0.0005, 0, TODAY));
        market.put("A", TestCandles.trend(400, 100, 0.004, 1_000, TODAY));
        market.put("B", TestCandles.trend(400, 100, 0.005, 1_000, TODAY));
        market.put("C", TestCandles.trend(400, 100, 0.006, 1_000, TODAY));

        EodRunSummary summary = service(List.of("A", "B", "C"), 2).run(false);

        assertThat(summary.bullish()).isEqualTo(2);
    }

    @Test
    void warnsWhenResultsFallInsideTheHoldingWindow() {
        market.put("^NSEI", TestCandles.trend(400, 20000, 0.0005, 0, TODAY));
        market.put("UPCO", TestCandles.trend(400, 100, 0.004, 1_000, TODAY));
        calendar = new EventCalendar(List.of(MarketEvent.results(TODAY.plusDays(6), "UPCO", Source.CONFIG)));

        service(List.of("UPCO"), 5).run(false);

        assertThat(bodies.getFirst()).contains("Results on 17 Nov (in 6 days)");
    }

    @Test
    void listsUpcomingEventsForTheWeek() {
        market.put("^NSEI", TestCandles.trend(400, 20000, 0.0005, 0, TODAY));
        market.put("UPCO", TestCandles.trend(400, 100, 0.004, 1_000, TODAY));
        calendar = new EventCalendar(List.of(
                MarketEvent.macro(TODAY.plusDays(3), "US CPI inflation data", Impact.HIGH),
                MarketEvent.macro(TODAY.plusDays(20), "Too far away", Impact.HIGH)));

        service(List.of("UPCO"), 5).run(false);

        assertThat(bodies.getFirst()).contains("UPCOMING EVENTS").contains("US CPI inflation data")
                .doesNotContain("Too far away");
    }

    private static IndexMember member(String symbol) {
        return new IndexMember(symbol, symbol, "");
    }

    private EodWatchlistService service(List<String> universe, int topN) {
        return new EodWatchlistService(market, new IndicatorCalculator(), new SwingScorer(),
                new AlertDispatcher(List.of(notifier)), calendar, clock, new SwingSettings(universe, "^NSEI", topN, 15), store);
    }

    private static class FakeMarketData implements MarketDataClient {
        private final Map<String, List<Candle>> data = new HashMap<>();

        void put(String symbol, List<Candle> candles) {
            data.put(symbol, candles);
        }

        @Override
        public List<Candle> dailyCandles(String symbol) {
            List<Candle> candles = data.get(symbol);
            if (candles == null) {
                throw new IllegalStateException("No data for " + symbol);
            }
            return candles;
        }
    }
}
