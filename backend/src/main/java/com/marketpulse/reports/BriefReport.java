package com.marketpulse.reports;

import com.marketpulse.brief.CueReading;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** The latest pre-market brief. {@code text} is the AI-written part (or the fallback note). */
public record BriefReport(Instant generatedAt, LocalDate date, String text, boolean aiWritten,
                          List<CueReading> cues, List<String> missingCues) {
}
