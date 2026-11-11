package com.marketpulse.tracking;

/** @param checked open trades looked at · closed trades that closed this run · failed stocks without data */
public record TrackingRunSummary(int checked, int closed, int failed) {
}
