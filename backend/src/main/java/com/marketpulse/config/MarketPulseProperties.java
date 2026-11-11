package com.marketpulse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "marketpulse")
public record MarketPulseProperties(
        String jobToken,
        @DefaultValue News news,
        @DefaultValue Claude claude,
        @DefaultValue Telegram telegram,
        @DefaultValue Email email) {

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
