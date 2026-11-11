package com.marketpulse.brief;

import com.marketpulse.events.MarketEvent;
import com.marketpulse.news.NewsItem;

import java.time.LocalDate;
import java.util.List;

/** Everything the brief writer may use. The writer must not add facts beyond this. */
public record BriefInput(LocalDate date, List<CueReading> cues, List<NewsItem> headlines,
                         List<MarketEvent> events, List<String> missingCues) {
}
