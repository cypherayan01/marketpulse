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
        @DefaultValue Brief brief) {

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
            @DefaultValue("5") int topN,
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
