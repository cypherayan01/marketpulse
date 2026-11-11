package com.marketpulse.tracking;

/** Results for a subset of closed trades, e.g. score 80-100 or confidence HIGH. */
public record Bucket(String label, int closed, double winRatePct, double avgReturnPct) {
}
