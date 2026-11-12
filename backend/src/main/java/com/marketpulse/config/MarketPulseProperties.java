package com.marketpulse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@ConfigurationProperties(prefix = "marketpulse")
public record MarketPulseProperties(
        String jobToken,
        @DefaultValue News news,
        @DefaultValue Claude claude,
        @DefaultValue Telegram telegram,
        @DefaultValue Email email,
        @DefaultValue Swing swing,
        @DefaultValue Events events,
        @DefaultValue Brief brief,
        @DefaultValue Positional positional,
        @DefaultValue Btst btst,
        @DefaultValue Dashboard dashboard) {

    public record News(
            @DefaultValue List<Feed> feeds,
            @DefaultValue("30m") Duration lookback,
            @DefaultValue("7") int impactThreshold,
            @DefaultValue("20") int batchSize,
            @DefaultValue List<String> keywords,
            @DefaultValue List<String> watchlist) {
    }

    public record Feed(String name, String url) {
    }

    public record Swing(
            @DefaultValue List<String> universe,
            @DefaultValue("^NSEI") String indexSymbol,
            @DefaultValue("6") int topN,
            @DefaultValue("15") int resultsWarningDays) {
    }

    /** Market calendar. Results dates announced in news are added automatically at runtime. */
    public record Events(
            @DefaultValue List<MacroEvent> macro,
            @DefaultValue List<ResultsDate> results,
            @DefaultValue List<LocalDate> holidays) {
    }

    public record MacroEvent(LocalDate date, String title, @DefaultValue("HIGH") String impact) {
    }

    public record ResultsDate(String symbol, LocalDate date) {
    }

    public record Brief(
            @DefaultValue List<Cue> cues,
            @DefaultValue("40") int maxHeadlines,
            @DefaultValue("3") int eventDays) {
    }

    public record Cue(String name, String symbol) {
    }

    /**
     * @param token          secret the React dashboard sends as "Authorization: Bearer ..."; dashboard API is off without it
     * @param allowedOrigins sites allowed to call /api/** from a browser (your Vercel URL)
     */
    public record Dashboard(String token, @DefaultValue("http://localhost:5173") List<String> allowedOrigins) {
    }

    /** @param universe stocks to scan; empty means "same as swing.universe" */
    public record Btst(
            @DefaultValue List<String> universe,
            @DefaultValue("3") int topN,
            @DefaultValue("10") int intradayPerSegment) {
    }

    /** @param universe stocks to scan; empty means "same as swing.universe" */
    public record Positional(
            @DefaultValue List<String> universe,
            @DefaultValue("6") int topN,
            @DefaultValue("14") int resultsWarningDays) {
    }

    public record Claude(
            String apiKey,
            @DefaultValue("claude-haiku-5-5") String triageModel,
            @DefaultValue("claude-opus-5-5") String briefModel) {
    }

    public record Telegram(String botToken, String chatId) {
        public boolean configured() {
            return hasText(botToken) && hasText(chatId);
        }
    }

    public record Email(String resendApiKey, String from, String to) {
        public boolean configured() {
            return hasText(resendApiKey) && hasText(from) && hasText(to);
        }
    }

    static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
