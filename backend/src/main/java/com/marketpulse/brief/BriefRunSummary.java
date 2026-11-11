package com.marketpulse.brief;

public record BriefRunSummary(int cues, int headlines, int events, boolean aiWritten, boolean sent,
                              String skippedReason) {

    static BriefRunSummary skipped(String reason) {
        return new BriefRunSummary(0, 0, 0, false, false, reason);
    }
}
