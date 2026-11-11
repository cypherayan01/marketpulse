package com.marketpulse.positional;

import java.util.List;

/**
 * @param resultsWarningDays suggest waiting for results when they're due within this many days
 */
public record PositionalSettings(List<String> universe, String indexSymbol, int topN, int resultsWarningDays) {
}
