package com.marketpulse.btst;

public record BtstRunSummary(int scanned, int failed, int candidates, boolean sent, String skippedReason) {

    static BtstRunSummary skipped(String reason) {
        return new BtstRunSummary(0, 0, 0, false, reason);
    }
}
