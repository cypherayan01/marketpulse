package com.marketpulse.brief;

import java.time.LocalDate;
import java.util.Set;

/**
 * @param holidays     NSE trading holidays (no brief on these days)
 * @param maxHeadlines most recent relevant headlines passed to the writer
 * @param eventDays    include calendar events from today up to this many days ahead
 */
public record BriefSettings(Set<LocalDate> holidays, int maxHeadlines, int eventDays) {
}
