package com.marketpulse.news;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Cheap keyword pre-filter so only potentially market-moving headlines are sent to the LLM.
 * Matches whole words/phrases, case-insensitively.
 */
public class NewsRelevanceFilter {

    private final Pattern pattern;

    public NewsRelevanceFilter(List<String> marketKeywords, List<String> watchlist) {
        String alternatives = Stream.concat(marketKeywords.stream(), watchlist.stream())
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        this.pattern = alternatives.isEmpty()
                ? null
                : Pattern.compile("\\b(?:" + alternatives + ")\\b");
    }

    public boolean isRelevant(NewsItem item) {
        if (pattern == null) {
            return false;
        }
        String text = (item.title() + " " + item.summary()).toLowerCase(Locale.ROOT);
        return pattern.matcher(text).find();
    }
}
