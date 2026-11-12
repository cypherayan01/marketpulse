package com.marketpulse.swing;

import com.marketpulse.universe.Universe;

import java.util.List;

/**
 * @param universe           stocks to scan, with their market-cap segments
 * @param indexSymbol        benchmark for market context and relative strength (^NSEI = Nifty 50)
 * @param topN               maximum bullish and bearish stocks per segment in the report
 * @param resultsWarningDays warn when a setup has results within this many days (your typical holding period)
 */
public record SwingSettings(Universe universe, String indexSymbol, int topN, int resultsWarningDays) {

    /** A plain symbol list, without segments. */
    public SwingSettings(List<String> symbols, String indexSymbol, int topN, int resultsWarningDays) {
        this(Universe.unsegmented(symbols), indexSymbol, topN, resultsWarningDays);
    }
}
