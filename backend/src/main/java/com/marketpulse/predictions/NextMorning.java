package com.marketpulse.predictions;

/**
 * BTST buy calls the morning after.
 *
 * @param gapUpPct share that opened above the signal-day close
 */
public record NextMorning(int measured, double gapUpPct, double avgOpenPct, double avgHighPct) {
}
