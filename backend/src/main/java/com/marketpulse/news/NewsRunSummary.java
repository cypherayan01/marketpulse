package com.marketpulse.news;

/** What happened during one news-poll run. */
public record NewsRunSummary(int fetched, int fresh, int relevant, int alerted) {
}
