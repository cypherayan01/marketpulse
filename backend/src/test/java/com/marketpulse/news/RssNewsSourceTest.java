package com.marketpulse.news;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RssNewsSourceTest {

    @Test
    void parsesItemsWithDatesAndStripsHtml() throws Exception {
        try (InputStream xml = getClass().getResourceAsStream("/rss/sample-feed.xml")) {
            List<NewsItem> items = RssNewsSource.parse("Sample", xml);

            assertThat(items).hasSize(2); // item without pubDate is skipped

            NewsItem first = items.getFirst();
            assertThat(first.id()).isEqualTo("rbi-holds-001");
            assertThat(first.title()).isEqualTo("RBI holds repo rate at 5.5%");
            assertThat(first.summary()).isEqualTo("The central bank kept rates unchanged.");
            assertThat(first.link()).isEqualTo("https://example.com/rbi-holds");
            assertThat(first.source()).isEqualTo("Sample");
            assertThat(first.publishedAt()).isEqualTo(Instant.parse("2026-11-10T04:30:00Z"));
        }
    }

    @Test
    void fallsBackToLinkAsIdWhenGuidMissing() throws Exception {
        try (InputStream xml = getClass().getResourceAsStream("/rss/sample-feed.xml")) {
            NewsItem second = RssNewsSource.parse("Sample", xml).get(1);

            assertThat(second.id()).isEqualTo("https://example.com/crude-jumps");
        }
    }
}
