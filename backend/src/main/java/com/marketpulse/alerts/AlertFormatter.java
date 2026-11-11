package com.marketpulse.alerts;

import com.marketpulse.news.Direction;
import com.marketpulse.news.NewsImpact;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Turns a {@link NewsImpact} into a human-readable alert. */
public final class AlertFormatter {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a 'IST'", Locale.ENGLISH).withZone(IST);

    private AlertFormatter() {
    }

    public static String subject(NewsImpact impact) {
        return "%s %s %d/10 · %s".formatted(icon(impact.direction()), impact.direction(),
                impact.impactScore(), impact.item().title());
    }

    public static String body(NewsImpact impact) {
        StringBuilder sb = new StringBuilder();
        sb.append(impact.reason()).append("\n\n");
        if (!impact.sectors().isEmpty()) {
            sb.append("Sectors: ").append(String.join(", ", impact.sectors())).append('\n');
        }
        if (!impact.stocks().isEmpty()) {
            sb.append("Stocks to watch: ").append(String.join(", ", impact.stocks())).append('\n');
        }
        sb.append('\n')
                .append("Source: ").append(impact.item().source())
                .append(" · ").append(TIME.format(impact.item().publishedAt())).append('\n')
                .append(impact.item().link()).append("\n\n")
                .append("Wait for price/volume confirmation before acting. Not investment advice.");
        return sb.toString();
    }

    private static String icon(Direction direction) {
        return switch (direction) {
            case BULLISH -> "🟢";
            case BEARISH -> "🔴";
            case MIXED -> "🟡";
            case NEUTRAL -> "⚪";
        };
    }
}
