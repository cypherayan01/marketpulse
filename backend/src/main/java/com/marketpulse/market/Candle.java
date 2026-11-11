package com.marketpulse.market;

import java.time.LocalDate;

/** One daily OHLCV bar. */
public record Candle(LocalDate date, double open, double high, double low, double close, long volume) {
}
