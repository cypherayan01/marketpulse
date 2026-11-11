package com.marketpulse.news;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Uses Claude to judge how a batch of headlines could move Indian markets.
 * The response is constrained to {@link ImpactReport} via structured outputs, so no free-text parsing is needed.
 */
public class ClaudeNewsImpactAnalyzer implements NewsImpactAnalyzer {

    static final String SYSTEM_PROMPT = """
            You are a market analyst for an Indian retail investor who trades NSE stocks with a swing \
            (3-20 day), BTST (overnight) and positional (weeks to months) horizon.

            For every numbered headline, assess its likely impact on Indian equity markets:
            - impactScore 1-10: 1-3 routine/noise, 4-6 notable for a sector or single stock, \
            7-8 likely to move a sector or the index, 9-10 market-wide shock (major policy surprise, war, crash).
            - direction: BULLISH, BEARISH, MIXED (winners and losers) or NEUTRAL, for Indian equities.
            - sectors: Indian sectors most affected (e.g. Banking, IT, Oil & Gas, Aviation, Metals, Pharma, Auto).
            - stocks: up to 5 NSE symbols (e.g. RELIANCE, HDFCBANK, INDIGO) that are directly affected. \
            Leave empty rather than guessing.
            - reason: one or two plain-English sentences explaining the mechanism (why it matters).
            - resultsSymbol / resultsDate: only when the headline states the date of a listed Indian company's \
            upcoming board meeting or announcement of financial results, give its NSE symbol and that date as \
            YYYY-MM-DD (use the headline's publication date to pick the year). Otherwise use empty strings.

            Base your judgement only on the headline text given. Do not invent prices, numbers or events. \
            Global news matters when it plausibly transmits to India (Fed, crude, dollar, US tech, China, wars). \
            Assess every headline exactly once, using its index number.""";

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("dd MMM HH:mm 'IST'", Locale.ENGLISH).withZone(ZoneId.of("Asia/Kolkata"));

    private final AnthropicClient client;
    private final String model;

    public ClaudeNewsImpactAnalyzer(AnthropicClient client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public List<NewsImpact> analyze(List<NewsItem> items) {
        StructuredMessageCreateParams<ImpactReport> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(8000L)
                .system(SYSTEM_PROMPT)
                .outputConfig(ImpactReport.class)
                .addUserMessage(buildPrompt(items))
                .build();

        StructuredMessage<ImpactReport> response = client.messages().create(params);

        if (response.stopReason().map(r -> r.asString().equals("refusal")).orElse(false)) {
            throw new IllegalStateException("Claude declined to assess this batch");
        }
        ImpactReport report = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(typed -> typed.text())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Claude returned no assessment"));
        return toImpacts(items, report);
    }

    static String buildPrompt(List<NewsItem> items) {
        StringBuilder sb = new StringBuilder("Assess these headlines:\n\n");
        for (int i = 0; i < items.size(); i++) {
            NewsItem item = items.get(i);
            sb.append('[').append(i).append("] ").append(item.title())
                    .append(" (").append(item.source()).append(", ").append(TIME.format(item.publishedAt())).append(")\n");
            if (item.summary() != null && !item.summary().isBlank()) {
                sb.append("    ").append(item.summary()).append('\n');
            }
        }
        return sb.toString();
    }

    static List<NewsImpact> toImpacts(List<NewsItem> items, ImpactReport report) {
        List<NewsImpact> impacts = new ArrayList<>();
        for (HeadlineAssessment a : Optional.ofNullable(report.assessments()).orElse(List.of())) {
            if (a.index() < 0 || a.index() >= items.size()) {
                continue; // model referenced a headline that does not exist
            }
            impacts.add(new NewsImpact(
                    items.get(a.index()),
                    Math.clamp(a.impactScore(), 1, 10),
                    a.direction() != null ? a.direction() : Direction.NEUTRAL,
                    Optional.ofNullable(a.sectors()).orElse(List.of()),
                    Optional.ofNullable(a.stocks()).orElse(List.of()),
                    Optional.ofNullable(a.reason()).orElse(""),
                    resultsAnnouncement(a.resultsSymbol(), a.resultsDate())));
        }
        return impacts;
    }

    private static ResultsAnnouncement resultsAnnouncement(String symbol, String date) {
        if (symbol == null || symbol.isBlank() || date == null || date.isBlank()) {
            return null;
        }
        try {
            return new ResultsAnnouncement(symbol.trim().toUpperCase(Locale.ROOT), LocalDate.parse(date.trim()));
        } catch (DateTimeParseException e) {
            return null; // the model wrote something that isn't an ISO date - ignore rather than guess
        }
    }

    /** Structured-output schema returned by Claude. */
    public record ImpactReport(
            @JsonPropertyDescription("One assessment per headline")
            List<HeadlineAssessment> assessments) {
    }

    public record HeadlineAssessment(
            @JsonPropertyDescription("Index number of the headline, as given in brackets")
            int index,
            @JsonPropertyDescription("Market impact from 1 (noise) to 10 (market-wide shock)")
            int impactScore,
            Direction direction,
            @JsonPropertyDescription("Affected Indian sectors")
            List<String> sectors,
            @JsonPropertyDescription("Directly affected NSE symbols, at most 5; empty if unsure")
            List<String> stocks,
            @JsonPropertyDescription("One or two sentences explaining why")
            String reason,
            @JsonPropertyDescription("NSE symbol whose upcoming results date the headline announces; empty string if none")
            String resultsSymbol,
            @JsonPropertyDescription("That results date as YYYY-MM-DD; empty string if none")
            String resultsDate) {
    }
}
