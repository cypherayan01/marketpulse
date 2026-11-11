package com.marketpulse.alerts;

import com.marketpulse.news.Direction;
import com.marketpulse.news.NewsImpact;
import com.marketpulse.news.NewsItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlertFormatterTest {

    private final NewsImpact impact = new NewsImpact(
            new NewsItem("1", "Crude oil jumps 6% after supply shock", "", "https://news/crude", "ET Markets",
                    Instant.parse("2026-11-11T05:00:00Z")),
            8, Direction.BEARISH, List.of("Aviation", "Oil marketing"), List.of("INDIGO", "BPCL"),
            "Higher fuel costs pressure airline and OMC margins.");

    @Test
    void subjectShowsDirectionScoreAndHeadline() {
        assertThat(AlertFormatter.subject(impact))
                .isEqualTo("🔴 BEARISH 8/10 · Crude oil jumps 6% after supply shock");
    }

    @Test
    void bodyContainsReasonSectorsStocksSourceAndIstTime() {
        String body = AlertFormatter.body(impact);

        assertThat(body)
                .contains("Higher fuel costs pressure airline and OMC margins.")
                .contains("Sectors: Aviation, Oil marketing")
                .contains("Stocks to watch: INDIGO, BPCL")
                .contains("ET Markets")
                .contains("https://news/crude")
                .contains("11 Nov 2026, 10:30 AM IST")
                .contains("Not investment advice");
    }

    @Test
    void bodyOmitsEmptyStockList() {
        NewsImpact noStocks = new NewsImpact(impact.item(), 7, Direction.MIXED, List.of("Banking"), List.of(), "r");

        assertThat(AlertFormatter.body(noStocks)).doesNotContain("Stocks to watch");
    }
}
