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

    private EodWatchlistService service(List<String> universe, int topN) {
        return new EodWatchlistService(market, new IndicatorCalculator(), new SwingScorer(),
                new AlertDispatcher(List.of(notifier)), calendar, clock, new SwingSettings(universe, "^NSEI", topN, 15));
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
