package com.marketpulse.brief;

/** A global market instrument to watch before the open, e.g. ("S&P 500", "^GSPC"). */
public record CueDefinition(String name, String symbol) {
}
