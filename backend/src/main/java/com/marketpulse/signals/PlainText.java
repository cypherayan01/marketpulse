package com.marketpulse.signals;

import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Removes Markdown that LLMs sometimes add despite instructions. Telegram messages and the dashboard show
 * text as-is, so "**Bottom line:**" would appear with the asterisks.
 */
public final class PlainText {

    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+");
    private static final Pattern STAR_BULLET = Pattern.compile("^(\\s*)\\*\\s+");
    private static final Pattern BOLD_STARS = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern BOLD_UNDERSCORES = Pattern.compile("__(.+?)__");
    private static final Pattern CODE = Pattern.compile("`([^`]*)`");

    private PlainText() {
    }

    public static String from(String text) {
        return text.lines()
                .map(line -> HEADING.matcher(line).replaceFirst(""))
                .map(line -> STAR_BULLET.matcher(line).replaceFirst("$1- "))
                .map(line -> BOLD_STARS.matcher(line).replaceAll("$1"))
                .map(line -> BOLD_UNDERSCORES.matcher(line).replaceAll("$1"))
                .map(line -> CODE.matcher(line).replaceAll("$1"))
                .map(String::stripTrailing)
                .collect(Collectors.joining("\n"))
                .strip();
    }
}
