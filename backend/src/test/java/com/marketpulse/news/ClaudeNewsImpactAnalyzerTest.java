package com.marketpulse.news;

import com.marketpulse.news.ClaudeNewsImpactAnalyzer.HeadlineAssessment;
import com.marketpulse.news.ClaudeNewsImpactAnalyzer.ImpactReport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeNewsImpactAnalyzerTest {

    private final List<NewsItem> items = List.of(
            new NewsItem("a", "RBI cuts repo rate", "Surprise 50 bps cut", "https://x/a", "ET",
                    Instant.parse("2026-11-11T05:00:00Z")),
            new NewsItem("b", "Crude falls 1%", "", "https://x/b", "Mint",
                    Instant.parse("2026-11-11T05:10:00Z")));

    @Test
    void promptNumbersEachHeadlineWithSourceAndSummary() {
        String prompt = ClaudeNewsImpactAnalyzer.buildPrompt(items);

        assertThat(prompt)
                .contains("[0] RBI cuts repo rate")
                .contains("Surprise 50 bps cut")
                .contains("(ET")
                .contains("[1] Crude falls 1%");
    }

    @Test
    void mapsAssessmentsBackToNewsItemsByIndex() {
        ImpactReport report = new ImpactReport(List.of(
                new HeadlineAssessment(1, 3, Direction.NEUTRAL, List.of("Energy"), List.of(), "Minor move"),
                new HeadlineAssessment(0, 9, Direction.BULLISH, List.of("Banking"), List.of("HDFCBANK"), "Cheaper credit")));

        List<NewsImpact> impacts = ClaudeNewsImpactAnalyzer.toImpacts(items, report);

        assertThat(impacts).hasSize(2);
        NewsImpact rbi = impacts.stream().filter(i -> i.item().id().equals("a")).findFirst().orElseThrow();
        assertThat(rbi.impactScore()).isEqualTo(9);
        assertThat(rbi.direction()).isEqualTo(Direction.BULLISH);
        assertThat(rbi.stocks()).containsExactly("HDFCBANK");
    }

    @Test
    void ignoresUnknownIndexesAndClampsScores() {
        ImpactReport report = new ImpactReport(List.of(
                new HeadlineAssessment(7, 9, Direction.BULLISH, List.of(), List.of(), "hallucinated index"),
                new HeadlineAssessment(0, 15, Direction.BULLISH, null, null, "too high")));

        List<NewsImpact> impacts = ClaudeNewsImpactAnalyzer.toImpacts(items, report);

        assertThat(impacts).hasSize(1);
        assertThat(impacts.getFirst().impactScore()).isEqualTo(10);
        assertThat(impacts.getFirst().sectors()).isEmpty();
    }
}
