package com.marketpulse.predictions;

/**
 * Results N sessions after the predictions.
 *
 * @param hitRatePct   share of calls that were right (beat Nifty for a buy, lagged it for an avoid)
 * @param avgEdgePct   average points the calls were better than Nifty (positive = the scan adds value)
 * @param avgReturnPct average raw move of the BUY calls, for reference
 */
public record Checkpoint(int sessions, int measured, double hitRatePct, double avgEdgePct, double avgReturnPct) {
}
