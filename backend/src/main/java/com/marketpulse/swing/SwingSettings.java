package com.marketpulse.swing;

import java.util.List;

/**
 * @param universe    NSE symbols to scan
 * @param indexSymbol benchmark for market context and relative strength (^NSEI = Nifty 50)
 * @param topN        maximum stocks per list in the report
 */
public record SwingSettings(List<String> universe, String indexSymbol, int topN) {
}
