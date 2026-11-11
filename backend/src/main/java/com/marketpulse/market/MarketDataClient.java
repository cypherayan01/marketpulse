package com.marketpulse.market;

import java.util.List;

/** Source of daily candles. Symbols are plain NSE symbols ("RELIANCE") or index codes ("^NSEI"). */
public interface MarketDataClient {
    /** Roughly two years of daily candles, oldest first. */
    List<Candle> dailyCandles(String symbol);
}
