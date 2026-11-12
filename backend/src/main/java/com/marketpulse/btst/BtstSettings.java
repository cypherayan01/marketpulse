package com.marketpulse.btst;

import com.marketpulse.universe.Universe;

import java.util.List;

/**
 * @param topN               maximum candidates per segment
 * @param intradayPerSegment how many of the best daily-candle pre-scores per segment get their 15-minute bars
 *                           fetched and a full score (keeps a 500-stock scan to a few minutes)
 */
public record BtstSettings(Universe universe, String indexSymbol, int topN, int intradayPerSegment) {

    /** A plain symbol list, without segments. */
    public BtstSettings(List<String> symbols, String indexSymbol, int topN) {
        this(Universe.unsegmented(symbols), indexSymbol, topN, 10);
    }
}
