package com.marketpulse.news;

import java.util.List;

/** The AI's assessment of how a headline could affect Indian markets. */
public record NewsImpact(NewsItem item, int impactScore, Direction direction,
                         List<String> sectors, List<String> stocks, String reason) {
}
