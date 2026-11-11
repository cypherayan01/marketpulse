package com.marketpulse.swing;

import com.marketpulse.market.Candle;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

/** Computes the daily-chart indicators a swing trader looks at, using ta4j. */
public class IndicatorCalculator {

    static final int MIN_BARS = 65;            // enough for 3-month return and the shorter indicators
    private static final int SESSIONS_3M = 63;
    private static final int SESSIONS_52W = 250;
    private static final int CROSS_LOOKBACK = 3;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    public IndicatorSnapshot calculate(String symbol, List<Candle> candles) {
        if (candles.size() < MIN_BARS) {
            throw new IllegalArgumentException(
                    "%s has only %d sessions of history; need at least %d".formatted(symbol, candles.size(), MIN_BARS));
        }
        BarSeries series = toSeries(symbol, candles);
        int last = series.getEndIndex();

        ClosePriceIndicator close = new ClosePriceIndicator(series);
        HighPriceIndicator high = new HighPriceIndicator(series);
        LowPriceIndicator low = new LowPriceIndicator(series);
        VolumeIndicator volume = new VolumeIndicator(series);
        MACDIndicator macd = new MACDIndicator(close, 12, 26);
        EMAIndicator macdSignal = macd.getSignalLine(9);

        return new IndicatorSnapshot(
                symbol,
                candles.getLast().date(),
                v(close, last),
                v(new EMAIndicator(close, 20), last),
                v(new EMAIndicator(close, 50), last),
                v(new EMAIndicator(close, 200), last),
                v(new RSIIndicator(close, 14), last),
                v(macd, last),
                v(macdSignal, last),
                macdCross(macd, macdSignal, last),
                v(new ATRIndicator(series, 14), last),
                candles.getLast().volume(),
                v(new SMAIndicator(volume, 20), last - 1),
                v(new HighestValueIndicator(high, 20), last - 1),
                v(new LowestValueIndicator(low, 20), last - 1),
                v(new HighestValueIndicator(high, SESSIONS_52W), last),
                v(new LowestValueIndicator(low, 10), last),
                v(close, last) / v(close, last - SESSIONS_3M) - 1,
                candles.size());
    }

    private static MacdCross macdCross(Indicator<Num> macd, Indicator<Num> signal, int last) {
        double diffNow = v(macd, last) - v(signal, last);
        for (int i = last - 1; i >= last - CROSS_LOOKBACK; i--) {
            double diffThen = v(macd, i) - v(signal, i);
            if (diffNow > 0 && diffThen <= 0) {
                return MacdCross.BULLISH;
            }
            if (diffNow < 0 && diffThen >= 0) {
                return MacdCross.BEARISH;
            }
        }
        return MacdCross.NONE;
    }

    private static BarSeries toSeries(String symbol, List<Candle> candles) {
        BarSeries series = new BaseBarSeriesBuilder().withName(symbol).build();
        for (Candle c : candles) {
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(c.date().atTime(15, 30).atZone(IST).toInstant())
                    .openPrice(c.open())
                    .highPrice(c.high())
                    .lowPrice(c.low())
                    .closePrice(c.close())
                    .volume(c.volume())
                    .add();
        }
        return series;
    }

    private static double v(Indicator<Num> indicator, int index) {
        return indicator.getValue(index).doubleValue();
    }
}
