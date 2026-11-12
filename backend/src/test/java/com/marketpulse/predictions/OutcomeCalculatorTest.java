package com.marketpulse.predictions;

import com.marketpulse.market.Candle;
import com.marketpulse.signals.Classification;
import com.marketpulse.signals.TradeStyle;
import com.marketpulse.universe.Segment;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class OutcomeCalculatorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 2); // a Monday
    private final OutcomeCalculator calculator = new OutcomeCalculator();

    @Test
    void measuresTheStockAndNiftyAtEachCheckpointAfterThePrediction() {
        Prediction p = buy(100);
        List<Candle> stock = series(100, 101, 103, 102, 104, 106, 105); // signal day + 6 sessions
        List<Candle> nifty = series(1000, 1005, 1010, 1010, 1015, 1020, 1020);

        Prediction checked = calculator.fill(p, stock, nifty);

        assertThat(checked.returns().get(1)).isCloseTo(1.0, within(1e-9));     // 100 -> 101
        assertThat(checked.returns().get(5)).isCloseTo(6.0, within(1e-9));     // 100 -> 106
        assertThat(checked.niftyReturns().get(5)).isCloseTo(2.0, within(1e-9)); // 1000 -> 1020
        assertThat(checked.returns()).doesNotContainKey(10);                    // not that far yet
    }

    @Test
    void recordsTheNextMorningForBtst() {
        List<Candle> stock = new ArrayList<>(series(100));
        stock.add(new Candle(DAY.plusDays(1), 101.5, 104, 99, 103, 1_000));

        Prediction checked = calculator.fill(buy(100), stock, series(1000, 1004));

        assertThat(checked.nextOpenPct()).isCloseTo(1.5, within(1e-9));
        assertThat(checked.nextHighPct()).isCloseTo(4.0, within(1e-9));
    }

    @Test
    void comparesWithNiftyOnTheSameDatesEvenIfTheStockSkippedADay() {
        List<Candle> stock = List.of(bar(0, 100), bar(1, 102), /* stock missing day 2 */ bar(3, 105));
        List<Candle> nifty = List.of(bar(0, 1000), bar(1, 1010), bar(2, 1020), bar(3, 1030));

        Prediction checked = calculator.fill(buy(100), stock, nifty);

        assertThat(checked.returns().get(2)).isCloseTo(5.0, within(1e-9));     // the stock's 2nd session after
        assertThat(checked.niftyReturns().get(2)).isCloseTo(3.0, within(1e-9)); // Nifty on that same date (day 3)
    }

    @Test
    void aBuyIsRightWhenItBeatsNiftyAndAnAvoidIsRightWhenItLags() {
        Prediction buy = buy(100).withOutcomes(java.util.Map.of(5, 3.0), java.util.Map.of(5, 1.0), null, null);
        Prediction avoid = avoid(100).withOutcomes(java.util.Map.of(5, 3.0), java.util.Map.of(5, 1.0), null, null);

        assertThat(buy.edgePct(5)).isCloseTo(2.0, within(1e-9));    // beat Nifty by 2 points: right
        assertThat(buy.correct(5)).contains(true);
        assertThat(avoid.edgePct(5)).isCloseTo(-2.0, within(1e-9)); // the stock to avoid beat Nifty: wrong
        assertThat(avoid.correct(5)).contains(false);
        assertThat(buy.correct(10)).isEmpty();                       // not measured yet
    }

    @Test
    void leavesCheckpointsEmptyWhenTheSignalDayIsMissing() {
        Prediction checked = calculator.fill(buy(100), List.of(bar(5, 110), bar(6, 111)), series(1000, 1010));

        assertThat(checked.returns()).isEmpty();
        assertThat(checked.nextOpenPct()).isNull();
    }

    private static Prediction buy(double price) {
        return Prediction.of(PredictionSource.LIVE, TradeStyle.SWING, "TEST", Segment.LARGE, DAY, 86,
                Classification.STRONG_BULLISH, Call.BUY, price, 1000, 0, Instant.EPOCH);
    }

    private static Prediction avoid(double price) {
        return Prediction.of(PredictionSource.LIVE, TradeStyle.SWING, "TEST", Segment.LARGE, DAY, 20,
                Classification.STRONG_BEARISH, Call.AVOID, price, 1000, 0, Instant.EPOCH);
    }

    private static List<Candle> series(double... closes) {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < closes.length; i++) {
            candles.add(bar(i, closes[i]));
        }
        return candles;
    }

    private static Candle bar(int day, double close) {
        return new Candle(DAY.plusDays(day), close, close, close, close, 1_000);
    }
}
