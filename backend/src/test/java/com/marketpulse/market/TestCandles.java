package com.marketpulse.market;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Builds synthetic daily candle series for tests. */
public final class TestCandles {

    private TestCandles() {
    }

    /** {@code count} candles ending on {@code lastDate}, closing price changing by {@code dailyPct} each day. */
    public static List<Candle> trend(int count, double startPrice, double dailyPct, long volume, LocalDate lastDate) {
        List<Candle> candles = new ArrayList<>();
        double close = startPrice;
        for (int i = 0; i < count; i++) {
            double open = close;
            close = close * (1 + dailyPct);
            double high = Math.max(open, close) * 1.005;
            double low = Math.min(open, close) * 0.995;
            candles.add(new Candle(lastDate.minusDays(count - 1 - i), open, high, low, close, volume));
        }
        return candles;
    }

    /** Copy of {@code candles} with the last candle replaced. */
    public static List<Candle> withLast(List<Candle> candles, Candle last) {
        List<Candle> copy = new ArrayList<>(candles.subList(0, candles.size() - 1));
        copy.add(last);
        return copy;
    }
}
