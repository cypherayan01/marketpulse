package com.marketpulse.positional;

import com.marketpulse.universe.Universe;

import java.util.List;

/**
 * @param topN               maximum bullish and bearish stocks per segment in the report
 * @param resultsWarningDays suggest waiting for results when they're due within this many days
 */
public record PositionalSettings(Universe universe, String indexSymbol, int topN, int resultsWarningDays) {

    /** A plain symbol list, without segments. */
    public PositionalSettings(List<String> symbols, String indexSymbol, int topN, int resultsWarningDays) {
        this(Universe.unsegmented(symbols), indexSymbol, topN, resultsWarningDays);
    }
}
