package com.marketpulse.swing;

/** Outcome of one end-of-day scan. {@code skippedReason} is set when no report was produced. */
public record EodRunSummary(int scanned, int failed, int bullish, int bearish, boolean sent, String skippedReason) {

    static EodRunSummary skipped(String reason) {
        return new EodRunSummary(0, 0, 0, 0, false, reason);
    }
}
