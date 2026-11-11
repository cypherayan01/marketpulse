package com.marketpulse.swing;

import java.util.List;

/**
 * @param universe    NSE symbols to scan
 * @param indexSymbol benchmark for market context and relative strength (^NSEI = Nifty 50)
 * @param topN        maximum stocks per list in the report
 * @param resultsWarningDays warn when a setup has results within this many days (your typical holding period)
 */
public record SwingSettings(List<String> universe, String indexSymbol, int topN, int resultsWarningDays) {
}
