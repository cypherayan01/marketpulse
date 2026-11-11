package com.marketpulse.reports;

import com.marketpulse.news.Direction;

import java.time.Instant;
import java.util.List;

/** A headline Claude has scored, with whether it was important enough to alert on. */
public record NewsEntry(String id, String title, String source, String link, Instant publishedAt, Instant scoredAt,
                        int impactScore, Direction direction, List<String> sectors, List<String> stocks,
                        String reason, boolean alerted) {
}
