package com.marketpulse.swing;

import com.marketpulse.market.Candle;
import com.marketpulse.market.TestCandles;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class IndicatorCalculatorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 11);
    private final IndicatorCalculator calculator = new IndicatorCalculator();

    @Test
    void steadyUptrendStacksEmasAndShowsStrongMomentum() {
        IndicatorSnapshot s = calculator.calculate("UP", TestCandles.trend(300, 100, 0.004, 1_000, DAY));

        assertThat(s.close()).isGreaterThan(s.ema20());
        assertThat(s.ema20()).isGreaterThan(s.ema50());
        assertThat(s.ema50()).isGreaterThan(s.ema200());
        assertThat(s.rsi14()).isGreaterThan(70);
        assertThat(s.return3m()).isPositive();
        assertThat(s.date()).isEqualTo(DAY);
        assertThat(s.barCount()).isEqualTo(300);
    }

    @Test
    void steadyDowntrendShowsWeakMomentum() {
        IndicatorSnapshot s = calculator.calculate("DOWN", TestCandles.trend(300, 100, -0.004, 1_000, DAY));

        assertThat(s.close()).isLessThan(s.ema20());
        assertThat(s.ema20()).isLessThan(s.ema50());
        assertThat(s.rsi14()).isLessThan(30);
        assertThat(s.return3m()).isNegative();
    }

    @Test
    void atrEqualsDailyRangeWhenEveryBarHasSameRange() {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            candles.add(new Candle(DAY.minusDays(249 - i), 100, 105, 95, 100, 1_000));
        }

        assertThat(calculator.calculate("FLAT", candles).atr14()).isCloseTo(10.0, within(0.01));
    }

    @Test
    void priorHighLowAndAverageVolumeExcludeToday() {
        List<Candle> base = TestCandles.trend(250, 100, 0.0, 1_000, DAY); // flat ~100, highs ~100.5
        Candle breakout = new Candle(DAY, 100, 112, 99, 110, 3_000);
        IndicatorSnapshot s = calculator.calculate("BRK", TestCandles.withLast(base, breakout));

        assertThat(s.prior20High()).isCloseTo(100.5, within(0.01));
        assertThat(s.prior20Low()).isCloseTo(99.5, within(0.01));
        assertThat(s.avgVolume20()).isCloseTo(1_000, within(0.01));
        assertThat(s.volume()).isEqualTo(3_000);
        assertThat(s.high52w()).isCloseTo(112, within(0.01));
    }

    @Test
    void detectsFreshBullishMacdCrossover() {
        List<Candle> candles = new ArrayList<>(TestCandles.trend(250, 200, -0.003, 1_000, DAY.minusDays(3)));
        double close = candles.getLast().close();
        // a sharp drop pushes MACD below its signal line, then two strong up days flip it back above
        double[] moves = {-0.05, 0.06, 0.06};
        for (int i = 0; i < moves.length; i++) {
            double next = close * (1 + moves[i]);
            candles.add(new Candle(DAY.minusDays(2 - i), close, Math.max(close, next) * 1.005,
                    Math.min(close, next) * 0.995, next, 1_000));
            close = next;
        }

        IndicatorSnapshot s = calculator.calculate("X", candles);

        assertThat(s.macd()).isGreaterThan(s.macdSignal());
        assertThat(s.macdCross()).isEqualTo(MacdCross.BULLISH);
    }

    @Test
    void noCrossoverDuringSteadyTrend() {
        IndicatorSnapshot s = calculator.calculate("UP", TestCandles.trend(300, 100, 0.004, 1_000, DAY));

        assertThat(s.macdCross()).isEqualTo(MacdCross.NONE);
    }

    @Test
    void twoHundredDayEmaIsMissingRatherThanMisleadingWithLessHistory() {
        IndicatorSnapshot s = calculator.calculate("NEWIPO", TestCandles.trend(150, 100, 0.004, 1_000, DAY));

        assertThat(s.ema200()).isNaN();
        assertThat(s.ema50()).isLessThan(s.close());
    }

    @Test
    void snapshotsForEveryPastDayNeverUseLaterData() {
        // a bumpy series: trend + swings, so every indicator moves
        List<Candle> candles = new ArrayList<>();
        double price = 100;
        for (int i = 0; i < 320; i++) {
            double move = 0.002 + 0.03 * Math.sin(i / 7.0);
            double open = price;
            price = price * (1 + move / 3);
            candles.add(new Candle(DAY.minusDays(319 - i), open, Math.max(open, price) * 1.01,
                    Math.min(open, price) * 0.99, price, 1_000 + (i % 13) * 300));
        }

        List<IndicatorSnapshot> history = calculator.snapshotsFrom("BUMPY", candles, 250);

        assertThat(history).hasSize(70);
        for (IndicatorSnapshot past : history) {
            int index = candles.indexOf(candles.stream().filter(c -> c.date().equals(past.date())).findFirst().orElseThrow());
            IndicatorSnapshot onlyDataUpToThatDay = calculator.calculate("BUMPY", candles.subList(0, index + 1));
            assertThat(past).usingRecursiveComparison()
                    .withComparatorForType((a, b) -> Math.abs(a - b) < 1e-6 || (a.isNaN() && b.isNaN()) ? 0 : 1, Double.class)
                    .isEqualTo(onlyDataUpToThatDay);
        }
    }

    @Test
    void rejectsSeriesTooShortForIndicators() {
        assertThatThrownBy(() -> calculator.calculate("NEW", TestCandles.trend(40, 100, 0.01, 1_000, DAY)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NEW");
    }
}
