package com.marketpulse.market;

import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;

/** Rolls daily candles up into ISO-week (Mon-Sun) candles dated on each week's last trading day. */
public final class WeeklyCandles {

    private WeeklyCandles() {
    }

    public static List<Candle> from(List<Candle> daily) {
        List<Candle> weekly = new ArrayList<>();
        Candle week = null;
        long weekKey = Long.MIN_VALUE;
        for (Candle d : daily) {
            long key = d.date().get(IsoFields.WEEK_BASED_YEAR) * 100L + d.date().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            if (week == null || key != weekKey) {
                if (week != null) {
                    weekly.add(week);
                }
                week = d;
                weekKey = key;
            } else {
                week = new Candle(d.date(), week.open(), Math.max(week.high(), d.high()), Math.min(week.low(), d.low()),
                        d.close(), week.volume() + d.volume());
            }
        }
        if (week != null) {
            weekly.add(week);
        }
        return weekly;
    }
}
