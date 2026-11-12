package com.marketpulse.predictions;

public record ReplaySummary(int days, int stocks, int failed, int predictions, long seconds) {
}
