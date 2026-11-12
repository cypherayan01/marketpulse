package com.marketpulse.predictions;

import com.marketpulse.market.Candle;
import com.marketpulse.market.MarketDataClient;
import com.marketpulse.market.TestCandles;
import com.marketpulse.signals.TradeStyle;
import com.marketpulse.universe.IndexMember;
import com.marketpulse.universe.Segment;
import com.marketpulse.universe.Universe;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);
    private final Map<String, List<Candle>> data = new HashMap<>();
    private final MarketDataClient market = symbol -> {
        List<Candle> c = data.get(symbol);
        if (c == null) throw new IllegalStateException("no data for " + symbol);
        return c;
    };
    private final InMemoryPredictions repository = new InMemoryPredictions();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneId.of("UTC"));
    private final Universe universe = Universe.of(List.of(m("UPCO"), m("DOWNCO")), List.of(), List.of(m("SMALLUP")),
            List.of(m("UPCO"), m("DOWNCO"), m("SMALLUP"), m("GHOST")));

    {
        data.put("^NSEI", TestCandles.trend(500, 20000, 0.0003, 0, TODAY));
        data.put("UPCO", TestCandles.trend(500, 100, 0.003, 1_000, TODAY));
        data.put("SMALLUP", TestCandles.trend(500, 100, 0.0035, 1_000, TODAY));
        data.put("DOWNCO", TestCandles.trend(500, 100, -0.003, 1_000, TODAY));
    }

    @Test
    void replaysPastDaysAndStoresTheCallsWithTheirOutcomes() {
        ReplaySummary summary = service(60).run();

        assertThat(summary.days()).isEqualTo(60);
        assertThat(summary.stocks()).isEqualTo(3);
        assertThat(summary.failed()).isEqualTo(1); // GHOST has no data
        assertThat(summary.predictions()).isEqualTo(repository.findAll(PredictionSource.REPLAY).size()).isPositive();
    }

    @Test
    void makesBuyCallsForUptrendsAndAvoidCallsForDowntrendsEachDay() {
        service(30).run();

        List<Prediction> swing = repository.findAll(PredictionSource.REPLAY).stream()
                .filter(p -> p.style() == TradeStyle.SWING).toList();
        assertThat(swing).anyMatch(p -> p.symbol().equals("UPCO") && p.call() == Call.BUY);
        assertThat(swing).anyMatch(p -> p.symbol().equals("SMALLUP") && p.call() == Call.BUY && p.segment() == Segment.SMALL);
        assertThat(swing).anyMatch(p -> p.symbol().equals("DOWNCO") && p.call() == Call.AVOID);
        assertThat(swing).allMatch(p -> p.source() == PredictionSource.REPLAY);
        assertThat(swing.stream().map(Prediction::signalDate).distinct()).hasSize(30);
    }

    @Test
    void fillsInOutcomesForDaysThatAreFarEnoughBack() {
        service(30).run();

        Prediction early = repository.findAll(PredictionSource.REPLAY).stream()
                .filter(p -> p.style() == TradeStyle.SWING && p.symbol().equals("UPCO"))
                .min(java.util.Comparator.comparing(Prediction::signalDate)).orElseThrow();
        Prediction last = repository.findAll(PredictionSource.REPLAY).stream()
                .filter(p -> p.style() == TradeStyle.SWING && p.symbol().equals("UPCO"))
                .max(java.util.Comparator.comparing(Prediction::signalDate)).orElseThrow();

        assertThat(early.returns()).containsKeys(1, 5, 10, 20);
        assertThat(early.correct(5)).contains(true); // steady riser beats a flat-ish Nifty
        assertThat(last.signalDate()).isEqualTo(TODAY);
        assertThat(last.returns()).isEmpty();        // nothing has happened after today yet
    }

    @Test
    void alsoReplaysPositionalWeeklyAndBtstDaily() {
        service(60).run();

        List<Prediction> all = repository.findAll(PredictionSource.REPLAY);
        assertThat(all).anyMatch(p -> p.style() == TradeStyle.POSITIONAL);
        assertThat(all.stream().filter(p -> p.style() == TradeStyle.POSITIONAL).map(Prediction::signalDate).distinct())
                .hasSizeBetween(8, 10); // about one per week
        assertThat(all).filteredOn(p -> p.style() == TradeStyle.BTST).allMatch(p -> p.call() == Call.BUY && p.score() >= 80);
    }

    @Test
    void aSecondReplayReplacesTheFirst() {
        service(30).run();
        int first = repository.findAll(PredictionSource.REPLAY).size();

        service(30).run();

        assertThat(repository.findAll(PredictionSource.REPLAY)).hasSize(first);
    }

    private ReplayService service(int sessions) {
        return new ReplayService(market, universe, "^NSEI", repository, new OutcomeCalculator(), clock,
                new ReplaySettings(sessions, 6, 3));
    }

    private static IndexMember m(String symbol) {
        return new IndexMember(symbol, symbol, "");
    }
}
