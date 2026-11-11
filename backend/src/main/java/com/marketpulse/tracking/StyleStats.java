package com.marketpulse.tracking;

import com.marketpulse.signals.TradeStyle;

import java.util.List;

/**
 * Paper-trading track record for one style. Percentages are of closed trades; returns are per trade in percent.
 * {@code avgReturnPct} is the expectancy: what an average recommendation returned.
 */
public record StyleStats(TradeStyle style, int open, int closed, int wins, double winRatePct,
                         double avgReturnPct, double avgWinPct, double avgLossPct,
                         int targetHits, int stops, int expired, double bestPct, double worstPct,
                         List<Bucket> byScore, List<Bucket> byConfidence) {
}
