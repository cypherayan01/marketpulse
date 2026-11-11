package com.marketpulse.news;

import java.time.LocalDate;

/** A company's upcoming results date, as announced in a headline. */
public record ResultsAnnouncement(String symbol, LocalDate date) {
}
