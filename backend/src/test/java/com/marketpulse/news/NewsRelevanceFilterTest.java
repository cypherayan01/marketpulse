package com.marketpulse.news;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewsRelevanceFilterTest {

    private final NewsRelevanceFilter filter = new NewsRelevanceFilter(
            List.of("rbi", "crude", "repo rate", "fed"),
            List.of("Reliance", "HDFC Bank"));

    @Test
    void matchesMarketKeywordIgnoringCase() {
        assertThat(filter.isRelevant(item("RBI keeps rates unchanged", ""))).isTrue();
    }

    @Test
    void matchesMultiWordKeyword() {
        assertThat(filter.isRelevant(item("Central bank hikes Repo Rate by 25 bps", ""))).isTrue();
    }

    @Test
    void matchesWatchlistStockInSummary() {
        assertThat(filter.isRelevant(item("Big deal announced", "Reliance signs pact with partner"))).isTrue();
    }

    @Test
    void doesNotMatchKeywordInsideAnotherWord() {
        // "fed" must not match "fedora" or "federation"
        assertThat(filter.isRelevant(item("Fedora launches new release", "Football federation news"))).isFalse();
    }

    @Test
    void ignoresIrrelevantNews() {
        assertThat(filter.isRelevant(item("Bollywood star buys new car", "Celebrity gossip"))).isFalse();
    }

    private static NewsItem item(String title, String summary) {
        return new NewsItem("id-" + title.hashCode(), title, summary, "https://x/" + title.hashCode(), "Test", Instant.now());
    }
}
