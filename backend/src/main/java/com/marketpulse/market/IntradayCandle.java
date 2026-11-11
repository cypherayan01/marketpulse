package com.marketpulse.market;

import java.time.Instant;

/** One intraday bar (15 minutes) starting at {@code start}. */
public record IntradayCandle(Instant start, double open, double high, double low, double close, long volume) {
}
