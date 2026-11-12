package com.marketpulse.predictions;

/** Results for a subset of predictions at the style's key checkpoint. */
public record Breakdown(String label, int measured, double hitRatePct, double avgEdgePct) {
}
