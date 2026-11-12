package com.marketpulse.predictions;

import com.marketpulse.market.Candle;
import com.marketpulse.market.MarketDataClient;
import com.marketpulse.signals.Classification;
import com.marketpulse.signals.TradeStyle;
import com.marketpulse.universe.Segment;
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
import static org.assertj.core.api.Assertions.within;

class PredictionUpdaterTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);
    private final InMemoryPredictions repository = new InMemoryPredictions();
    private final Map<String, List<Candle>> data = new HashMap<>();
    private final List<String> requests = new ArrayList<>();
    private final MarketDataClient market = symbol -> {
        requests.add(symbol);
        List<Candle> c = data.get(symbol);
        if (c == null) throw new IllegalStateException("no data for " + symbol);
        return c;
    };
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T11:00:00Z"), ZoneId.of("UTC"));
    private final PredictionUpdater updater = new PredictionUpdater(repository, market, new OutcomeCalculator(), "^NSEI", clock);

    @Test
    void fillsInTheCheckpointsThatHavePassed() {
        repository.saveIfNew(buy("TCS"));
        data.put("TCS", closes(100, 101, 102, 103, 104, 105, 106));
        data.put("^NSEI", closes(1000, 1001, 1002, 1003, 1004, 1005, 1006));

        PredictionUpdateSummary summary = updater.update();

        assertThat(summary).isEqualTo(new PredictionUpdateSummary(1, 1, 0));
        Prediction p = repository.rows.getFirst();
        assertThat(p.returns().get(5)).isCloseTo(5.0, within(1e-9));
        assertThat(p.niftyReturns().get(5)).isCloseTo(0.5, within(1e-9));
        assertThat(p.nextOpenPct()).isNotNull();
    }

    @Test
    void fetchesEachStockOnceAndNiftyOnce() {
        repository.saveIfNew(buy("TCS"));
        repository.saveIfNew(buy("TCS").withOutcomes(Map.of(), Map.of(), null, null));
        repository.saveIfNew(Prediction.of(PredictionSource.LIVE, TradeStyle.BTST, "TCS", Segment.LARGE, DAY, 90,
                Classification.STRONG_BULLISH, Call.BUY, 100, 1000, 0, Instant.EPOCH));
        data.put("TCS", closes(100, 101));
        data.put("^NSEI", closes(1000, 1001));

        updater.update();

        assertThat(requests).containsExactlyInAnyOrder("^NSEI", "TCS");
    }

    @Test
    void aStockWithoutDataIsCountedAndSkipped() {
        repository.saveIfNew(buy("GHOST"));
        data.put("^NSEI", closes(1000, 1001));

        assertThat(updater.update().failed()).isEqualTo(1);
    }

    private static Prediction buy(String symbol) {
        return Prediction.of(PredictionSource.LIVE, TradeStyle.SWING, symbol, Segment.LARGE, DAY, 86,
                Classification.STRONG_BULLISH, Call.BUY, 100, 1000, 0, Instant.EPOCH);
    }

    private static List<Candle> closes(double... values) {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            candles.add(new Candle(DAY.plusDays(i), values[i], values[i], values[i], values[i], 0));
        }
        return candles;
    }
}
