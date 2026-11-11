package com.marketpulse.positional;

import com.marketpulse.market.Candle;
import com.marketpulse.market.WeeklyCandles;
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

/** Weekly-chart indicators for positional (1-6 month) trades, from daily candles rolled up into weeks. */
public class PositionalCalculator {

    static final int MIN_WEEKS = 45; // 40-week average plus 4 weeks to see its slope
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    public PositionalSnapshot calculate(String symbol, List<Candle> daily) {
        List<Candle> weekly = WeeklyCandles.from(daily);
        if (weekly.size() < MIN_WEEKS) {
            throw new IllegalArgumentException(
                    "%s has only %d weeks of history; need at least %d weeks".formatted(symbol, weekly.size(), MIN_WEEKS));
        }
        BarSeries series = new BaseBarSeriesBuilder().withName(symbol).build();
        for (Candle w : weekly) {
            series.barBuilder()
                    .timePeriod(Duration.ofDays(7))
                    .endTime(w.date().atTime(15, 30).atZone(IST).toInstant())
                    .openPrice(w.open()).highPrice(w.high()).lowPrice(w.low()).closePrice(w.close())
                    .volume(w.volume())
                    .add();
        }
        int last = series.getEndIndex();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        HighPriceIndicator high = new HighPriceIndicator(series);
        LowPriceIndicator low = new LowPriceIndicator(series);
        SMAIndicator sma40 = new SMAIndicator(close, 40);
        MACDIndicator macd = new MACDIndicator(close, 12, 26);

        return new PositionalSnapshot(
                symbol,
                weekly.getLast().date(),
                v(close, last),
                v(new EMAIndicator(close, 10), last),
                v(new EMAIndicator(close, 30), last),
                v(sma40, last),
                v(sma40, last - 4),
                v(new RSIIndicator(close, 14), last),
                v(macd, last),
                v(macd.getSignalLine(9), last),
                v(new ATRIndicator(series, 10), last),
                weekly.getLast().volume(),
                v(new SMAIndicator(new VolumeIndicator(series), 10), last - 1),
                v(new HighestValueIndicator(high, 26), last - 1),
                v(new LowestValueIndicator(low, 26), last - 1),
                v(new HighestValueIndicator(high, 52), last),
                v(new LowestValueIndicator(low, 8), last),
                v(close, last) / v(close, last - 26) - 1,
                weekly.size());
    }

    private static double v(Indicator<Num> indicator, int index) {
        return indicator.getValue(index).doubleValue();
    }
}
