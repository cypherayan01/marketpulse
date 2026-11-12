package com.marketpulse.market;

import java.util.List;
import java.util.Optional;

/** Source of daily candles. Symbols are plain NSE symbols ("RELIANCE") or index codes ("^NSEI"). */
public interface MarketDataClient {
    /** Roughly two years of daily candles, oldest first. */
    List<Candle> dailyCandles(String symbol);

    /** Latest price vs previous close; empty when the source can't provide one. */
    default Optional<Quote> quote(String symbol) {
        return Optional.empty();
    }
}
