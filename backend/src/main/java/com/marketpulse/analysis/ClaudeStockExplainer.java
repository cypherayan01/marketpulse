package com.marketpulse.analysis;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.marketpulse.holdings.HoldingCheck;
import com.marketpulse.reports.NewsEntry;
import com.marketpulse.signals.TradePlan;
import com.marketpulse.signals.PlainText;
import com.marketpulse.signals.TradeSignal;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;

import static com.marketpulse.signals.Money.rupees;

/**
 * Explains a stock analysis in plain English with Claude. The app computes every number; the model only
 * interprets them. Server-side refusal fallback is enabled, as for the pre-market brief.
 */
public class ClaudeStockExplainer implements StockExplainer {

    static final String SYSTEM_PROMPT = """
            You explain a rule-based stock analysis to an Indian retail investor who trades NSE stocks (swing: \
            days to weeks; positional: months). Use ONLY the facts provided - never invent prices, levels, news, \
            results dates or fundamentals. If something important is missing, say so.

            Write plain text, at most ~180 words. No Markdown at all - no asterisks, bold, # headings or tables; \
            it is shown exactly as written:
            1. One sentence: the bottom line for this person (if they own it: keep, review or exit; otherwise: \
            buy, buy only for a short swing, accumulate slowly, wait, or avoid) and the main reason.
            2. "Why:" 2-3 short bullets in everyday words (explain jargon like EMA or RSI in a few words).
            3. "Watch out:" 1-2 bullets with the biggest risks (overbought, results, weak market, trend breaks).
            4. "Plan:" the entry / stop / target or the exit level from the facts, if given.
            Hedge appropriately ("likely", "could"); this is decision support, not a prediction or advice.""";

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);

    private final AnthropicClient client;
    private final String model;

    public ClaudeStockExplainer(AnthropicClient client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public String explain(StockAnalysis analysis) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(SYSTEM_PROMPT)
                .addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
                .fallbacksDefault()
                .addUserMessage(buildPrompt(analysis))
                .build();
        BetaMessage response = client.beta().messages().create(params);
        if (response.stopReason().map(r -> r.asString().equals("refusal")).orElse(false)) {
            throw new IllegalStateException("Claude declined to explain this analysis");
        }
        String text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(t -> t.text())
                .collect(Collectors.joining("\n"));
        text = PlainText.from(text); // Telegram and the dashboard show text as-is
        if (text.isEmpty()) {
            throw new IllegalStateException("Claude returned an empty explanation");
        }
        return text;
    }

    static String buildPrompt(StockAnalysis a) {
        StringBuilder sb = new StringBuilder();
        sb.append(a.symbol()).append(" (").append(a.name()).append(")");
        if (!a.industry().isBlank()) sb.append(" · ").append(a.industry());
        if (a.segment() != null) sb.append(" · ").append(a.segment().title());
        sb.append('\n');
        sb.append(String.format(Locale.ENGLISH, "Price %s (%+.2f%% on %s)%n", rupees(a.price()), a.dayChangePct(),
                DAY.format(a.priceDate())));
        sb.append("Verdict for a new buyer: ").append(a.verdict()).append(" - ").append(a.headline()).append('\n');
        a.reasons().forEach(r -> sb.append("- ").append(r).append('\n'));

        appendView(sb, "SWING", a.swing(), null);
        appendView(sb, "POSITIONAL", a.positional(), a.positionalUnavailable());

        if (a.resultsDate() != null) {
            sb.append("Results due ").append(DAY.format(a.resultsDate())).append('\n');
        }
        if (!a.news().isEmpty()) {
            sb.append("Recent scored news:\n");
            for (NewsEntry n : a.news()) {
                sb.append("- ").append(n.title()).append(" (impact ").append(n.impactScore()).append("/10, ")
                        .append(n.direction()).append("): ").append(n.reason()).append('\n');
            }
        }

        HoldingCheck h = a.holding();
        if (h == null) {
            sb.append("You don't own it.\n");
        } else {
            sb.append(String.format(Locale.ENGLISH, "You own %d shares bought at %s on %s (%s horizon). P&L %+.2f%%.%n",
                    h.holding().quantity(), rupees(h.holding().buyPrice()), DAY.format(h.holding().buyDate()),
                    h.holding().horizon(), h.pnlPct()));
            sb.append("Holding check: ").append(h.verdict()).append(" · active stop ").append(rupees(h.stop()))
                    .append(" (").append(h.stopSource()).append(")\n");
            h.reasons().forEach(r -> sb.append("- ").append(r).append('\n'));
            h.notes().forEach(n -> sb.append("- note: ").append(n).append('\n'));
        }
        return sb.toString();
    }

    private static void appendView(StringBuilder sb, String name, TradeSignal s, String unavailable) {
        if (s == null) {
            sb.append(name).append(": not available").append(unavailable != null ? " - " + unavailable : "").append('\n');
            return;
        }
        sb.append("%s: %d/100 %s, confidence %s%n".formatted(name, s.score(), s.classification().label(), s.confidence()));
        TradePlan p = s.plan();
        if (p != null) {
            sb.append("  Entry %s, stop %s, target %s%n".formatted(rupees(p.entry()), rupees(p.stopLoss()), rupees(p.target())));
        }
        if (s.invalidation() != null) sb.append("  Invalidated if: ").append(s.invalidation()).append('\n');
        s.bullishFactors().forEach(f -> sb.append("  + ").append(f).append('\n'));
        s.bearishFactors().forEach(f -> sb.append("  - ").append(f).append('\n'));
    }
}
