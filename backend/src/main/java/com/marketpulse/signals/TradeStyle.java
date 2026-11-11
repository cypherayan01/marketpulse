package com.marketpulse.signals;

/** Holding horizon a signal is scored for. */
public enum TradeStyle {
    /** Buy today, sell tomorrow. */
    BTST,
    /** A few days to ~3 weeks, daily chart. */
    SWING,
    /** One to six months, weekly chart. */
    POSITIONAL
}
