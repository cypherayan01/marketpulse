package com.marketpulse.brief;

import com.marketpulse.events.Impact;
import com.marketpulse.events.MarketEvent;
import com.marketpulse.news.NewsItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeBriefWriterTest {

    @Test
    void promptContainsComputedNumbersHeadlinesAndEvents() {
        BriefInput input = new BriefInput(
                LocalDate.of(2026, 11, 11),
                List.of(new CueReading("S&P 500", "^GSPC", 7599.04, 1.0386, LocalDate.of(2026, 11, 10))),
                List.of(new NewsItem("1", "Fed signals pause", "Officials see inflation easing", "https://x", "CNBC",
                        Instant.parse("2026-11-11T01:00:00Z"))),
                List.of(MarketEvent.macro(LocalDate.of(2026, 11, 11), "US CPI inflation data", Impact.HIGH)),
                List.of("Gold"));

        String prompt = ClaudeBriefWriter.buildPrompt(input);

        assertThat(prompt)
                .contains("Wednesday 11 Nov 2026")
                .contains("S&P 500: 7,599.04 (+1.04%) as of 10 Nov")
                .contains("Fed signals pause").contains("Officials see inflation easing")
                .contains("US CPI inflation data").contains("HIGH")
                .contains("Unavailable today: Gold");
    }
}
