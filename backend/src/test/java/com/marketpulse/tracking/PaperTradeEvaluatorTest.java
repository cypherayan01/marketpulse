package com.marketpulse.tracking;

import com.marketpulse.market.Candle;
import com.marketpulse.signals.Classification;
import com.marketpulse.signals.Confidence;
import com.marketpulse.signals.TradeStyle;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PaperTradeEvaluatorTest {

    private static final LocalDate SIGNAL_DAY = LocalDate.of(2026, 11, 11);
    private final PaperTradeEvaluator evaluator = new PaperTradeEvaluator();

    @Test
    void closesAtTargetWhenTheHighReachesIt() {
        TrackedSignal trade = swing(100, 95, 110);
        List<Candle> candles = history(
                bar(1, 100, 104, 99, 103),
                bar(2, 103, 111, 102, 108)); // high 111 >= target 110

        TrackedSignal result = evaluator.evaluate(trade, candles);

        assertThat(result.status()).isEqualTo(TradeStatus.TARGET_HIT);
        assertThat(result.exitPrice()).isEqualTo(110);
        assertThat(result.exitDate()).isEqualTo(SIGNAL_DAY.plusDays(2));
        assertThat(result.returnPct()).isCloseTo(10.0, within(1e-9));
        assertThat(result.sessionsHeld()).isEqualTo(2);
        assertThat(result.maxGainPct()).isCloseTo(11.0, within(1e-9));
        assertThat(result.maxLossPct()).isCloseTo(-1.0, within(1e-9));
    }

    @Test
    void closesAtStopWhenTheLowReachesIt() {
        TrackedSignal result = evaluator.evaluate(swing(100, 95, 110), history(bar(1, 99, 100, 94, 96)));

        assertThat(result.status()).isEqualTo(TradeStatus.STOPPED);
        assertThat(result.exitPrice()).isEqualTo(95);
        assertThat(result.returnPct()).isCloseTo(-5.0, within(1e-9));
    }

    @Test
    void assumesTheStopCameFirstWhenBothAreHitTheSameDay() {
        TrackedSignal result = evaluator.evaluate(swing(100, 95, 110), history(bar(1, 100, 112, 94, 105)));

        assertThat(result.status()).isEqualTo(TradeStatus.STOPPED); // conservative: can't know the intraday order
    }

    @Test
    void gapDownBelowTheStopExitsAtTheOpen() {
        TrackedSignal result = evaluator.evaluate(swing(100, 95, 110), history(bar(1, 90, 92, 88, 91)));

        assertThat(result.exitPrice()).isEqualTo(90); // you can't sell at 95 if it opens at 90
        assertThat(result.returnPct()).isCloseTo(-10.0, within(1e-9));
    }

    @Test
    void gapUpAboveTheTargetExitsAtTheOpen() {
        TrackedSignal result = evaluator.evaluate(swing(100, 95, 110), history(bar(1, 113, 115, 112, 114)));

        assertThat(result.status()).isEqualTo(TradeStatus.TARGET_HIT);
        assertThat(result.exitPrice()).isEqualTo(113);
    }

    @Test
    void expiresAtTheCloseAfterTheHoldingPeriod() {
        List<Candle> bars = new ArrayList<>();
        for (int day = 1; day <= 20; day++) {
            bars.add(bar(day, 101, 102, 99, 101.5)); // drifts sideways, never hits stop or target
        }

        TrackedSignal result = evaluator.evaluate(swing(100, 95, 110), history(bars.toArray(Candle[]::new)));

        assertThat(result.status()).isEqualTo(TradeStatus.EXPIRED);
        assertThat(result.sessionsHeld()).isEqualTo(PaperTradeEvaluator.SWING_SESSIONS);
        assertThat(result.exitDate()).isEqualTo(SIGNAL_DAY.plusDays(PaperTradeEvaluator.SWING_SESSIONS));
        assertThat(result.returnPct()).isCloseTo(1.5, within(1e-9));
    }

    @Test
    void staysOpenButRecordsTheMoveSoFar() {
        TrackedSignal result = evaluator.evaluate(swing(100, 95, 110), history(bar(1, 100, 106, 98, 105)));

        assertThat(result.status()).isEqualTo(TradeStatus.OPEN);
        assertThat(result.sessionsHeld()).isEqualTo(1);
        assertThat(result.maxGainPct()).isCloseTo(6.0, within(1e-9));
        assertThat(result.exitPrice()).isNull();
    }

    @Test
    void ignoresCandlesUpToAndIncludingTheSignalDay() {
        List<Candle> candles = List.of(
                new Candle(SIGNAL_DAY.minusDays(1), 80, 200, 10, 90, 0), // before: must not trigger anything
                new Candle(SIGNAL_DAY, 99, 101, 50, 100, 0));             // the signal day itself

        assertThat(evaluator.evaluate(swing(100, 95, 110), candles).status()).isEqualTo(TradeStatus.OPEN);
    }

    @Test
    void btstExitsAtTheNextMorningsOpen() {
        TrackedSignal btst = TrackedSignal.open(TradeStyle.BTST, "TRENT", SIGNAL_DAY, 90, Classification.STRONG_BULLISH,
                Confidence.HIGH, 100, 98, 103, Instant.EPOCH);

        TrackedSignal result = evaluator.evaluate(btst, history(bar(1, 101.5, 104, 100, 103.5)));

        assertThat(result.status()).isEqualTo(TradeStatus.EXPIRED);
        assertThat(result.exitPrice()).isEqualTo(101.5); // rule: exit by 10:30 -> the open is the daily proxy
        assertThat(result.returnPct()).isCloseTo(1.5, within(1e-9));
    }

    @Test
    void btstGapUpThroughTheTargetCountsAsTargetHit() {
        TrackedSignal btst = TrackedSignal.open(TradeStyle.BTST, "TRENT", SIGNAL_DAY, 90, Classification.STRONG_BULLISH,
                Confidence.HIGH, 100, 98, 103, Instant.EPOCH);

        assertThat(evaluator.evaluate(btst, history(bar(1, 104, 105, 102, 103))).status())
                .isEqualTo(TradeStatus.TARGET_HIT);
    }

    // ---------- helpers ----------

    private static TrackedSignal swing(double entry, double stop, double target) {
        return TrackedSignal.open(TradeStyle.SWING, "HDFCBANK", SIGNAL_DAY, 86, Classification.STRONG_BULLISH,
                Confidence.HIGH, entry, stop, target, Instant.EPOCH);
    }

    private static Candle bar(int daysAfter, double open, double high, double low, double close) {
        return new Candle(SIGNAL_DAY.plusDays(daysAfter), open, high, low, close, 1_000);
    }

    private static List<Candle> history(Candle... after) {
        List<Candle> candles = new ArrayList<>();
        candles.add(new Candle(SIGNAL_DAY, 99, 101, 98, 100, 1_000));
        candles.addAll(List.of(after));
        return candles;
    }
}
