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
import java.util.ArrayList;
import java.util.List;

/** Weekly-chart indicators for positional (1-6 month) trades, from daily candles rolled up into weeks. */
public class PositionalCalculator {

    static final int MIN_WEEKS = 45; // 40-week average plus 4 weeks to see its slope
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    public PositionalSnapshot calculate(String symbol, List<Candle> daily) {
        List<Candle> weekly = WeeklyCandles.from(daily);
        requireHistory(symbol, weekly.size());
        return snapshotAt(symbol, weekly, new Indicators(toSeries(symbol, weekly)), weekly.size() - 1);
    }

    /**
     * Snapshots at the end of every week from {@code firstWeek} on, building the weekly indicators once. Each one
     * equals {@link #calculate} on the candles up to that week only, so a replay never sees the future.
     */
    public List<PositionalSnapshot> weeklySnapshots(String symbol, List<Candle> daily, int firstWeek) {
        List<Candle> weekly = WeeklyCandles.from(daily);
        requireHistory(symbol, weekly.size());
        Indicators indicators = new Indicators(toSeries(symbol, weekly));
        List<PositionalSnapshot> snapshots = new ArrayList<>();
        for (int w = Math.max(firstWeek, MIN_WEEKS - 1); w < weekly.size(); w++) {
            snapshots.add(snapshotAt(symbol, weekly, indicators, w));
        }
        return snapshots;
    }

    private static void requireHistory(String symbol, int weeks) {
        if (weeks < MIN_WEEKS) {
            throw new IllegalArgumentException(
                    "%s has only %d weeks of history; need at least %d weeks".formatted(symbol, weeks, MIN_WEEKS));
        }
    }

    private static BarSeries toSeries(String symbol, List<Candle> weekly) {
        BarSeries series = new BaseBarSeriesBuilder().withName(symbol).build();
        for (Candle w : weekly) {
            series.barBuilder()
                    .timePeriod(Duration.ofDays(7))
                    .endTime(w.date().atTime(15, 30).atZone(IST).toInstant())
                    .openPrice(w.open()).highPrice(w.high()).lowPrice(w.low()).closePrice(w.close())
                    .volume(w.volume())
                    .add();
        }
        return series;
    }

    private static final class Indicators {
        final ClosePriceIndicator close;
        final EMAIndicator ema10, ema30;
        final SMAIndicator sma40, avgVolume;
        final RSIIndicator rsi;
        final MACDIndicator macd;
        final EMAIndicator macdSignal;
        final ATRIndicator atr;
        final HighestValueIndicator high26, high52;
        final LowestValueIndicator low26, low8;

        Indicators(BarSeries series) {
            close = new ClosePriceIndicator(series);
            HighPriceIndicator high = new HighPriceIndicator(series);
            LowPriceIndicator low = new LowPriceIndicator(series);
            ema10 = new EMAIndicator(close, 10);
            ema30 = new EMAIndicator(close, 30);
            sma40 = new SMAIndicator(close, 40);
            rsi = new RSIIndicator(close, 14);
            macd = new MACDIndicator(close, 12, 26);
            macdSignal = macd.getSignalLine(9);
            atr = new ATRIndicator(series, 10);
            avgVolume = new SMAIndicator(new VolumeIndicator(series), 10);
            high26 = new HighestValueIndicator(high, 26);
            low26 = new LowestValueIndicator(low, 26);
            high52 = new HighestValueIndicator(high, 52);
            low8 = new LowestValueIndicator(low, 8);
        }
    }

    private static PositionalSnapshot snapshotAt(String symbol, List<Candle> weekly, Indicators x, int w) {
        return new PositionalSnapshot(
                symbol,
                weekly.get(w).date(),
                v(x.close, w),
                v(x.ema10, w),
                v(x.ema30, w),
                v(x.sma40, w),
                v(x.sma40, w - 4),
                v(x.rsi, w),
                v(x.macd, w),
                v(x.macdSignal, w),
                v(x.atr, w),
                weekly.get(w).volume(),
                v(x.avgVolume, w - 1),
                v(x.high26, w - 1),
                v(x.low26, w - 1),
                v(x.high52, w),
                v(x.low8, w),
                v(x.close, w) / v(x.close, w - 26) - 1,
                w + 1);
    }

    private static double v(Indicator<Num> indicator, int index) {
        return indicator.getValue(index).doubleValue();
    }
}
