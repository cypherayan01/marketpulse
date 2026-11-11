package com.marketpulse.market;

import java.util.List;

/** Today's intraday bars. Symbols as in {@link MarketDataClient}. */
@FunctionalInterface
public interface IntradayDataClient {
    /** Today's 15-minute bars so far, oldest first (empty before the open). */
    List<IntradayCandle> intradayCandles(String symbol);
}
