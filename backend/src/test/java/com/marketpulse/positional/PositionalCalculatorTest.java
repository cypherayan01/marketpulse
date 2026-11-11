package com.marketpulse.positional;

import com.marketpulse.market.Candle;
import com.marketpulse.market.TestCandles;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PositionalCalculatorTest {

    private static final LocalDate FRIDAY = LocalDate.of(2026, 11, 13);
    private final PositionalCalculator calculator = new PositionalCalculator();

    @Test
    void longUptrendStacksWeeklyAveragesAndRisingLongTermTrend() {
        PositionalSnapshot s = calculator.calculate("UP", TestCandles.trend(600, 100, 0.002, 1_000, FRIDAY));

        assertThat(s.close()).isGreaterThan(s.ema10w());
        assertThat(s.ema10w()).isGreaterThan(s.ema30w());
        assertThat(s.ema30w()).isGreaterThan(s.sma40w());
        assertThat(s.sma40w()).isGreaterThan(s.sma40wFourWeeksAgo());
        assertThat(s.return6m()).isPositive();
        assertThat(s.weekEnding()).isEqualTo(FRIDAY);
        assertThat(s.weeks()).isBetween(85, 88);
    }

    @Test
    void longDowntrendHasFallingLongTermTrend() {
        PositionalSnapshot s = calculator.calculate("DOWN", TestCandles.trend(600, 100, -0.002, 1_000, FRIDAY));

        assertThat(s.close()).isLessThan(s.sma40w());
        assertThat(s.sma40w()).isLessThan(s.sma40wFourWeeksAgo());
        assertThat(s.rsi14w()).isLessThan(30);
    }

    @Test
    void sixMonthHighExcludesTheCurrentWeek() {
        List<Candle> daily = new ArrayList<>(TestCandles.trend(600, 100, 0.0, 1_000, FRIDAY.minusDays(7)));
        // a final breakout week far above the flat range
        for (int i = 4; i >= 0; i--) { // Mon 9 Nov .. Fri 13 Nov
            daily.add(new Candle(FRIDAY.minusDays(i), 100, 130, 99, 125, 1_000));
        }

        PositionalSnapshot s = calculator.calculate("BRK", daily);

        assertThat(s.prior26wHigh()).isLessThan(101);
        assertThat(s.close()).isEqualTo(125);
        assertThat(s.high52w()).isEqualTo(130);
    }

    @Test
    void rejectsLessThanAboutTenMonthsOfHistory() {
        assertThatThrownBy(() -> calculator.calculate("NEW", TestCandles.trend(200, 100, 0.01, 1_000, FRIDAY)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NEW").hasMessageContaining("weeks");
    }
}
