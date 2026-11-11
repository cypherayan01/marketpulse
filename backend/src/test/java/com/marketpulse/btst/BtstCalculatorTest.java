package com.marketpulse.btst;

import com.marketpulse.market.Candle;
import com.marketpulse.market.IntradayCandle;
import com.marketpulse.market.TestCandles;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BtstCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);
    private final BtstCalculator calculator = new BtstCalculator();

    @Test
    void describesTodaysSessionSoFar() {
        List<Candle> daily = new ArrayList<>(TestCandles.trend(250, 100, 0.0, 1_000, TODAY.minusDays(1)));
        double prevClose = daily.getLast().close();
        daily.add(new Candle(TODAY, 100, 104, 99, 103, 1_840)); // session in progress
        List<IntradayCandle> intraday = bars(101.0, 101.5, 102.0, 102.5, 103.0); // last 5 x 15-min closes

        BtstSnapshot s = calculator.calculate("TCS", daily, intraday, 0.92);

        assertThat(s.price()).isEqualTo(103);
        assertThat(s.prevClose()).isEqualTo(prevClose);
        assertThat(s.dayChangePct()).isCloseTo((103 / prevClose - 1) * 100, within(1e-9));
        assertThat(s.closeLocation()).isCloseTo(0.8, within(1e-9));           // (103-99)/(104-99)
        assertThat(s.projectedVolumeRatio()).isCloseTo(2.0, within(1e-9));    // 1,840 / 0.92 / 1,000
        assertThat(s.lastHourChangePct()).isCloseTo((103 / 101.0 - 1) * 100, within(1e-9));
        assertThat(s.prior20High()).isCloseTo(100.5, within(0.01));
    }

    @Test
    void lastHourIsZeroWithoutEnoughIntradayBars() {
        List<Candle> daily = TestCandles.trend(250, 100, 0.001, 1_000, TODAY);

        assertThat(calculator.calculate("TCS", daily, bars(100, 101), 1.0).lastHourChangePct()).isZero();
    }

    @Test
    void flatRangeHasNeutralCloseLocation() {
        List<Candle> daily = new ArrayList<>(TestCandles.trend(250, 100, 0.0, 1_000, TODAY.minusDays(1)));
        daily.add(new Candle(TODAY, 100, 100, 100, 100, 1_000));

        assertThat(calculator.calculate("TCS", daily, List.of(), 1.0).closeLocation()).isEqualTo(0.5);
    }

    private static List<IntradayCandle> bars(double... closes) {
        List<IntradayCandle> bars = new ArrayList<>();
        Instant start = Instant.parse("2026-11-11T08:00:00Z");
        for (int i = 0; i < closes.length; i++) {
            bars.add(new IntradayCandle(start.plusSeconds(900L * i), closes[i], closes[i], closes[i], closes[i], 100));
        }
        return bars;
    }
}
