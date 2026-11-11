package com.marketpulse.positional;

import java.time.LocalDate;

/**
 * Weekly-chart indicator values for one stock.
 *
 * @param sma40w             40-week simple average, roughly the 200-day average
 * @param sma40wFourWeeksAgo the same average four weeks earlier (is the long-term trend rising?)
 * @param prior26wHigh       highest high of the 26 weeks before this one (6-month breakout level)
 * @param prior26wLow        lowest low of the 26 weeks before this one
 * @param avgVolume10w       average weekly volume of the 10 weeks before this one
 * @param swingLow8w         lowest low of the last 8 weeks
 * @param return6m           price change over 26 weeks, as a fraction
 */
public record PositionalSnapshot(String symbol, LocalDate weekEnding, double close,
                                 double ema10w, double ema30w, double sma40w, double sma40wFourWeeksAgo,
                                 double rsi14w, double macd, double macdSignal, double atr10w,
                                 long volume, double avgVolume10w,
                                 double prior26wHigh, double prior26wLow, double high52w, double swingLow8w,
                                 double return6m, int weeks) {
}
