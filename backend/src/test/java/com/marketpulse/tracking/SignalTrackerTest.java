package com.marketpulse.tracking;

import com.marketpulse.market.Candle;
import com.marketpulse.market.MarketDataClient;
import com.marketpulse.signals.Classification;
import com.marketpulse.signals.Confidence;
import com.marketpulse.signals.TradeStyle;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SignalTrackerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 11);
    private final InMemorySignalRepository repository = new InMemorySignalRepository();
    private final Map<String, List<Candle>> data = new HashMap<>();
    private final List<String> requested = new ArrayList<>();
    private final MarketDataClient market = symbol -> {
        requested.add(symbol);
        List<Candle> c = data.get(symbol);
        if (c == null) throw new IllegalStateException("no data for " + symbol);
        return c;
    };
    private final SignalTracker tracker = new SignalTracker(repository, market, new PaperTradeEvaluator());

    @Test
    void closesTradesThatHitTheirTargetAndKeepsOthersOpen() {
        repository.saveIfNew(trade(TradeStyle.SWING, "WINNER"));
        repository.saveIfNew(trade(TradeStyle.SWING, "WAITING"));
        data.put("WINNER", List.of(day(0, 100, 101, 99, 100), day(1, 101, 112, 100, 111)));
        data.put("WAITING", List.of(day(0, 100, 101, 99, 100), day(1, 100, 103, 99, 102)));

        TrackingRunSummary summary = tracker.update();

        assertThat(summary).isEqualTo(new TrackingRunSummary(2, 1, 0));
        assertThat(repository.findOpen()).extracting(TrackedSignal::symbol).containsExactly("WAITING");
        assertThat(repository.findOpen().getFirst().maxGainPct()).isCloseTo(3.0, org.assertj.core.api.Assertions.within(1e-9));
    }

    @Test
    void fetchesEachStockOnceEvenWithTradesInSeveralStyles() {
        repository.saveIfNew(trade(TradeStyle.SWING, "TRENT"));
        repository.saveIfNew(trade(TradeStyle.BTST, "TRENT"));
        data.put("TRENT", List.of(day(0, 100, 101, 99, 100), day(1, 101, 102, 100, 101)));

        tracker.update();

        assertThat(requested).containsExactly("TRENT");
    }

    @Test
    void missingDataIsCountedAndLeavesTheTradeOpen() {
        repository.saveIfNew(trade(TradeStyle.SWING, "GHOST"));

        TrackingRunSummary summary = tracker.update();

        assertThat(summary.failed()).isEqualTo(1);
        assertThat(repository.findOpen()).hasSize(1);
    }

    private static TrackedSignal trade(TradeStyle style, String symbol) {
        return TrackedSignal.open(style, symbol, DAY, 85, Classification.STRONG_BULLISH, Confidence.HIGH,
                100, 95, 110, Instant.EPOCH);
    }

    private static Candle day(int offset, double open, double high, double low, double close) {
        return new Candle(DAY.plusDays(offset), open, high, low, close, 1_000);
    }
}
