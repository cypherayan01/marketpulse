package com.marketpulse.news;

import java.util.List;

/**
 * The AI's assessment of how a headline could affect Indian markets.
 *
 * @param results set when the headline announces a company's upcoming results date, otherwise null
 */
public record NewsImpact(NewsItem item, int impactScore, Direction direction,
                         List<String> sectors, List<String> stocks, String reason, ResultsAnnouncement results) {

    public NewsImpact(NewsItem item, int impactScore, Direction direction,
                      List<String> sectors, List<String> stocks, String reason) {
        this(item, impactScore, direction, sectors, stocks, reason, null);
    }
}
