package com.marketpulse.holdings;

import com.marketpulse.alerts.AlertDispatcher;
import com.marketpulse.events.EventCalendar;
import com.marketpulse.events.MarketEvent;
import com.marketpulse.events.Source;
import com.marketpulse.market.Candle;
import com.marketpulse.market.MarketDataClient;
import com.marketpulse.market.TestCandles;
import com.marketpulse.reports.ReportStore;
import com.marketpulse.signals.TradeStyle;
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

class HoldingsCheckServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);
    private final Clock clock = Clock.fixed(TODAY.atTime(16, 35).atZone(IST).toInstant(), IST);

    private final Map<String, List<Candle>> data = new HashMap<>();
    private final MarketDataClient market = symbol -> {
        List<Candle> c = data.get(symbol);
        if (c == null) throw new IllegalStateException("no data for " + symbol);
        return c;
    };
    private final InMemoryHoldings holdings = new InMemoryHoldings();
    private final List<String> subjects = new ArrayList<>();
    private final List<String> bodies = new ArrayList<>();
    private final AlertDispatcher dispatcher = new AlertDispatcher(List.of((s, b) -> { subjects.add(s); bodies.add(b); }));
    private final ReportStore store = new ReportStore(100);
    private EventCalendar calendar = new EventCalendar(List.of());

    @Test
    void checksEveryHoldingAndListsExitsFirst() {
        data.put("^NSEI", TestCandles.trend(600, 20000, 0.0003, 0, TODAY));
        data.put("STRONG", TestCandles.trend(300, 100, 0.002, 1_000, TODAY));
        data.put("BROKEN", crashToday(TestCandles.trend(300, 100, 0.002, 1_000, TODAY.minusDays(1))));
        double strong = data.get("STRONG").getLast().close();
        holdings.add(holding("STRONG", strong * 0.9, null));
        holdings.add(holding("BROKEN", 120, null));

        HoldingsRunSummary summary = service().run(false);

        assertThat(summary).isEqualTo(new HoldingsRunSummary(2, 1, 0, 1, 0, true, null));
        assertThat(subjects.getFirst()).contains("Holdings check").contains("1 exit").contains("1 hold");
        String body = bodies.getFirst();
        assertThat(body.indexOf("BROKEN")).isLessThan(body.indexOf("STRONG"));
        assertThat(body).contains("EXIT").contains("HOLD").contains("P&L");
    }

    @Test
    void savesTheCheckForTheDashboard() {
        data.put("^NSEI", TestCandles.trend(600, 20000, 0.0003, 0, TODAY));
        data.put("STRONG", TestCandles.trend(300, 100, 0.002, 1_000, TODAY));
        holdings.add(holding("STRONG", 50, null));

        service().run(false);

        var report = store.latestHoldings().orElseThrow();
        assertThat(report.session()).isEqualTo(TODAY);
        assertThat(report.checks()).extracting(c -> c.holding().symbol()).containsExactly("STRONG");
    }

    @Test
    void usesResultsDatesFromTheCalendar() {
        data.put("^NSEI", TestCandles.trend(600, 20000, 0.0003, 0, TODAY));
        data.put("STRONG", TestCandles.trend(300, 100, 0.002, 1_000, TODAY));
        holdings.add(holding("STRONG", 50, null));
        calendar = new EventCalendar(List.of(MarketEvent.results(TODAY.plusDays(3), "STRONG", Source.CONFIG)));

        HoldingsRunSummary summary = service().run(false);

        assertThat(summary.reviews()).isEqualTo(1);
    }

    @Test
    void sendsNothingWithoutHoldings() {
        data.put("^NSEI", TestCandles.trend(600, 20000, 0.0003, 0, TODAY));

        HoldingsRunSummary summary = service().run(false);

        assertThat(summary.sent()).isFalse();
        assertThat(summary.skippedReason()).contains("No holdings");
        assertThat(subjects).isEmpty();
    }

    @Test
    void skipsOnHolidaysUnlessForced() {
        data.put("^NSEI", TestCandles.trend(600, 20000, 0.0003, 0, TODAY.minusDays(1)));
        data.put("STRONG", TestCandles.trend(300, 100, 0.002, 1_000, TODAY.minusDays(1)));
        holdings.add(holding("STRONG", 50, null));

        assertThat(service().run(false).skippedReason()).contains("closed");
        assertThat(service().run(true).sent()).isTrue();
    }

    @Test
    void stockWithoutDataIsReportedNotFatal() {
        data.put("^NSEI", TestCandles.trend(600, 20000, 0.0003, 0, TODAY));
        data.put("STRONG", TestCandles.trend(300, 100, 0.002, 1_000, TODAY));
        holdings.add(holding("STRONG", 50, null));
        holdings.add(holding("GHOST", 50, null));

        HoldingsRunSummary summary = service().run(false);

        assertThat(summary.failed()).isEqualTo(1);
        assertThat(bodies.getFirst()).contains("Data unavailable: GHOST");
    }

    private HoldingsCheckService service() {
        return new HoldingsCheckService(holdings, market, new HoldingEvaluator(), calendar, dispatcher, store, clock, "^NSEI");
    }

    private static Holding holding(String symbol, double buyPrice, Double stop) {
        return new Holding(null, symbol, 10, buyPrice, TODAY.minusDays(60), TradeStyle.SWING, stop, null, Instant.EPOCH);
    }

    private static List<Candle> crashToday(List<Candle> history) {
        List<Candle> candles = new ArrayList<>(history);
        double prev = history.getLast().close();
        candles.add(new Candle(TODAY, prev, prev, prev * 0.88, prev * 0.89, 3_000));
        return candles;
    }

    private static class InMemoryHoldings implements HoldingRepository {
        private final List<Holding> rows = new ArrayList<>();
        private long nextId = 1;

        @Override
        public Holding add(Holding h) {
            Holding saved = new Holding(nextId++, h.symbol(), h.quantity(), h.buyPrice(), h.buyDate(), h.horizon(),
                    h.stopLoss(), h.target(), h.createdAt());
            rows.add(saved);
            return saved;
        }

        @Override
        public List<Holding> findAll() {
            return List.copyOf(rows);
        }

        @Override
        public boolean delete(long id) {
            return rows.removeIf(h -> h.id() == id);
        }
    }
}
