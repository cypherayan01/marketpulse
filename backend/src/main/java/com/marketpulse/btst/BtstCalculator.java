package com.marketpulse.btst;

import com.marketpulse.market.Candle;
import com.marketpulse.market.IntradayCandle;
import com.marketpulse.swing.IndicatorCalculator;
import com.marketpulse.swing.IndicatorSnapshot;

import java.util.List;

/** Builds a {@link BtstSnapshot} from daily candles (last one = today, in progress) and today's 15-minute bars. */
public class BtstCalculator {

    private static final int BARS_PER_HOUR = 4;

    private final IndicatorCalculator daily = new IndicatorCalculator();

    public BtstSnapshot calculate(String symbol, List<Candle> candles, List<IntradayCandle> intraday,
                                  double sessionFraction) {
        return fromSnapshot(daily.calculate(symbol, candles), candles.getLast(), candles.get(candles.size() - 2),
                intraday, sessionFraction);
    }

    /** Same, from an already computed daily snapshot (used by the history replay). */
    public BtstSnapshot fromSnapshot(IndicatorSnapshot d, Candle today, Candle yesterday, List<IntradayCandle> intraday,
                                     double sessionFraction) {
        double lastHour = 0;
        if (intraday.size() > BARS_PER_HOUR) {
            double hourAgo = intraday.get(intraday.size() - 1 - BARS_PER_HOUR).close();
            lastHour = (intraday.getLast().close() / hourAgo - 1) * 100;
        }
        return new BtstSnapshot(d.symbol(), today.date(), today.close(), today.open(), today.high(), today.low(),
                yesterday.close(), today.volume(), d.avgVolume20(), sessionFraction, d.prior20High(),
                d.ema20(), d.ema50(), d.rsi14(), d.atr14(), lastHour, d.barCount());
    }
}
