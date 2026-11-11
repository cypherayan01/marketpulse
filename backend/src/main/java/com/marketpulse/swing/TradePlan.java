package com.marketpulse.swing;

/** Reference levels for a long swing trade; riskPct is the distance to the stop as a percentage of entry. */
public record TradePlan(double entry, double stopLoss, double target, double riskPct) {
}
