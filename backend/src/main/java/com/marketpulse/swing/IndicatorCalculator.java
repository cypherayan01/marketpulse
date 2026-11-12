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
import java.util.ArrayList;
import java.util.List;

/** Computes the daily-chart indicators a swing trader looks at, using ta4j. */
public class IndicatorCalculator {

    static final int MIN_BARS = 65;            // enough for 3-month return and the shorter indicators
    private static final int SESSIONS_3M = 63;
    private static final int SESSIONS_52W = 250;
    private static final int CROSS_LOOKBACK = 3;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    public IndicatorSnapshot calculate(String symbol, List<Candle> candles) {
        requireHistory(symbol, candles.size());
        return snapshotAt(symbol, candles, new Indicators(toSeries(symbol, candles)), candles.size() - 1);
    }

    /**
     * Snapshots for every day from {@code firstIndex} to the end, building the indicators once. Each snapshot
     * equals {@link #calculate} on the candles up to that day only (indicators are causal), so a history replay
     * never sees the future.
     */
    public List<IndicatorSnapshot> snapshotsFrom(String symbol, List<Candle> candles, int firstIndex) {
        requireHistory(symbol, candles.size());
        Indicators indicators = new Indicators(toSeries(symbol, candles));
        List<IndicatorSnapshot> snapshots = new ArrayList<>();
        for (int i = Math.max(firstIndex, MIN_BARS - 1); i < candles.size(); i++) {
            snapshots.add(snapshotAt(symbol, candles, indicators, i));
        }
        return snapshots;
    }

    private static void requireHistory(String symbol, int bars) {
        if (bars < MIN_BARS) {
            throw new IllegalArgumentException(
                    "%s has only %d sessions of history; need at least %d".formatted(symbol, bars, MIN_BARS));
        }
    }

    /** All indicators over one series; ta4j caches values, so reading many indices is cheap. */
    private static final class Indicators {
        final ClosePriceIndicator close;
        final EMAIndicator ema20, ema50, ema200;
        final RSIIndicator rsi;
        final MACDIndicator macd;
        final EMAIndicator macdSignal;
        final ATRIndicator atr;
        final SMAIndicator avgVolume;
        final HighestValueIndicator high20, high52w;
        final LowestValueIndicator low20, low10;

        Indicators(BarSeries series) {
            close = new ClosePriceIndicator(series);
            HighPriceIndicator high = new HighPriceIndicator(series);
            LowPriceIndicator low = new LowPriceIndicator(series);
            ema20 = new EMAIndicator(close, 20);
            ema50 = new EMAIndicator(close, 50);
            ema200 = new EMAIndicator(close, 200);
            rsi = new RSIIndicator(close, 14);
            macd = new MACDIndicator(close, 12, 26);
            macdSignal = macd.getSignalLine(9);
            atr = new ATRIndicator(series, 14);
            avgVolume = new SMAIndicator(new VolumeIndicator(series), 20);
            high20 = new HighestValueIndicator(high, 20);
            low20 = new LowestValueIndicator(low, 20);
            high52w = new HighestValueIndicator(high, SESSIONS_52W);
            low10 = new LowestValueIndicator(low, 10);
        }
    }

    private static IndicatorSnapshot snapshotAt(String symbol, List<Candle> candles, Indicators x, int i) {
        return new IndicatorSnapshot(
                symbol,
                candles.get(i).date(),
                v(x.close, i),
                v(x.ema20, i),
                v(x.ema50, i),
                v(x.ema200, i),
                v(x.rsi, i),
                v(x.macd, i),
                v(x.macdSignal, i),
                macdCross(x.macd, x.macdSignal, i),
                v(x.atr, i),
                candles.get(i).volume(),
                v(x.avgVolume, i - 1),
                v(x.high20, i - 1),
                v(x.low20, i - 1),
                v(x.high52w, i),
                v(x.low10, i),
                v(x.close, i) / v(x.close, i - SESSIONS_3M) - 1,
                i + 1);
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
