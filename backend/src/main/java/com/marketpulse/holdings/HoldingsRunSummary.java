package com.marketpulse.holdings;

public record HoldingsRunSummary(int checked, int exits, int reviews, int holds, int failed, boolean sent,
                                 String skippedReason) {

    static HoldingsRunSummary skipped(String reason) {
        return new HoldingsRunSummary(0, 0, 0, 0, 0, false, reason);
    }
}
