package com.marketpulse.analysis;

/** What someone who doesn't own the stock should consider, combining the swing and positional views. */
public enum AnalysisVerdict {
    /** Short- and long-term trends both bullish. */
    BUY,
    /** Short-term setup only; the long-term trend isn't confirmed. */
    BUY_SWING,
    /** Long-term trend fine, short-term weak - wait for a better entry or buy in parts. */
    ACCUMULATE,
    /** Mixed signals. */
    WAIT,
    /** Trend down - don't buy (in the cash market "sell" means exit if you hold). */
    AVOID
}
