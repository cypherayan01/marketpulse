package com.marketpulse.swing;

import java.time.LocalDate;

/**
 * Indicator values for the latest session of one stock.
 *
 * @param prior20High highest high of the 20 sessions before today (the breakout level)
 * @param prior20Low  lowest low of the 20 sessions before today (the breakdown level)
 * @param avgVolume20 average volume of the 20 sessions before today
 * @param swingLow10  lowest low of the last 10 sessions (nearby support)
 * @param return3m    price change over ~63 sessions, as a fraction (0.12 = +12%)
 */
public record IndicatorSnapshot(String symbol, LocalDate date, double close,
                                double ema20, double ema50, double ema200,
                                double rsi14, double macd, double macdSignal, MacdCross macdCross,
                                double atr14, long volume, double avgVolume20,
                                double prior20High, double prior20Low, double high52w, double swingLow10,
                                double return3m, int barCount) {
}
