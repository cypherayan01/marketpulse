package com.marketpulse.news;

import java.time.Instant;

/** A single headline fetched from a news feed. */
public record NewsItem(String id, String title, String summary, String link, String source, Instant publishedAt) {
}
