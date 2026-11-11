package com.marketpulse.brief;

import java.time.LocalDate;

/** Latest value of a cue and its change from the previous session, computed by us (never by the LLM). */
public record CueReading(String name, String symbol, double last, double changePct, LocalDate asOf) {
}
