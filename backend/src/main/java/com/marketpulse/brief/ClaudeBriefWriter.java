package com.marketpulse.brief;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.marketpulse.events.MarketEvent;
import com.marketpulse.news.NewsItem;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Writes the pre-market brief with Claude. Numbers come from the app; the model only interprets them.
 * Server-side refusal fallback is enabled so a rare policy decline is retried on a fallback model.
 */
public class ClaudeBriefWriter implements BriefWriter {

    static final String SYSTEM_PROMPT = """
            You write a short pre-market brief for an Indian retail investor who trades NSE stocks with a \
            swing (3-20 days), BTST (overnight) and positional (weeks-months) horizon. It is read on a phone \
            in Telegram at 8:30 AM IST, before the 9:15 open.

            Use ONLY the numbers, headlines and events provided. Never invent prices, levels, percentages, \
            dates or news. If something important is missing, say it is unavailable rather than guessing.

            Format: plain text, no markdown tables, no headings with #. At most ~250 words. Structure:
            1. One line: overall mood for Indian equities today (Positive / Negative / Mixed / Cautious) and why.
            2. "Key drivers:" 2-4 short bullets linking global moves and news to India (e.g. crude -> OMCs, \
            aviation, paints; US tech -> Indian IT; dollar/yields -> FII flows; Asia -> opening sentiment).
            3. "Sectors to watch:" 1-3 bullets, each naming the sector, likely direction, and the reason.
            4. "Today's events:" list them if any, with why they matter; omit the section if none.
            5. One line on risk (e.g. high India VIX, event risk, gap-up/gap-down chasing).
            Hedge appropriately ("likely", "could") - these are possibilities, not predictions.""";

    private static final DateTimeFormatter TODAY = DateTimeFormatter.ofPattern("EEEE dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter EVENT_DAY = DateTimeFormatter.ofPattern("EEE dd MMM", Locale.ENGLISH);
    private static final DateTimeFormatter NEWS_TIME =
            DateTimeFormatter.ofPattern("dd MMM HH:mm", Locale.ENGLISH).withZone(ZoneId.of("Asia/Kolkata"));

    private final AnthropicClient client;
    private final String model;

    public ClaudeBriefWriter(AnthropicClient client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public String write(BriefInput input) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(SYSTEM_PROMPT)
                .addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
                .fallbacksDefault()
                .addUserMessage(buildPrompt(input))
                .build();

        BetaMessage response = client.beta().messages().create(params);

        if (response.stopReason().map(r -> r.asString().equals("refusal")).orElse(false)) {
            throw new IllegalStateException("Claude declined to write the brief");
        }
        String text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(t -> t.text())
                .collect(Collectors.joining("\n"))
                .strip();
        if (text.isEmpty()) {
            throw new IllegalStateException("Claude returned an empty brief");
        }
        return text;
    }

    static String buildPrompt(BriefInput input) {
        StringBuilder sb = new StringBuilder();
        sb.append("Today is ").append(TODAY.format(input.date())).append(" (IST).\n\n");

        sb.append("GLOBAL CUES (last close and % change vs the previous session, computed by the app):\n");
        input.cues().forEach(c -> sb.append("- ").append(BriefFormatter.cueLine(c)).append('\n'));
        if (!input.missingCues().isEmpty()) {
            sb.append("Unavailable today: ").append(String.join(", ", input.missingCues())).append('\n');
        }

        sb.append("\nHEADLINES SINCE THE LAST INDIAN SESSION CLOSED (newest first):\n");
        if (input.headlines().isEmpty()) {
            sb.append("- none\n");
        }
        for (NewsItem n : input.headlines()) {
            sb.append("- ").append(n.title()).append(" (").append(n.source()).append(", ")
                    .append(NEWS_TIME.format(n.publishedAt())).append(" IST)\n");
            if (n.summary() != null && !n.summary().isBlank()) {
                sb.append("  ").append(n.summary()).append('\n');
            }
        }

        sb.append("\nEVENTS (today and the next few days):\n");
        if (input.events().isEmpty()) {
            sb.append("- none scheduled\n");
        }
        for (MarketEvent e : input.events()) {
            sb.append("- ").append(EVENT_DAY.format(e.date())).append(": ").append(e.title())
                    .append(" [").append(e.impact()).append(" impact]\n");
        }
        return sb.toString();
    }
}
