package com.marketpulse.events;

import java.time.LocalDate;
import java.util.Locale;

/** A dated event that can move the market. {@code symbol} is set only for company results. */
public record MarketEvent(LocalDate date, EventType type, String symbol, String title, Impact impact, Source source) {

    public static MarketEvent macro(LocalDate date, String title, Impact impact) {
        return new MarketEvent(date, EventType.MACRO, null, title, impact, Source.CONFIG);
    }

    public static MarketEvent results(LocalDate date, String symbol, Source source) {
        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        return new MarketEvent(date, EventType.RESULTS, normalized, normalized + " quarterly results", Impact.MEDIUM, source);
    }
}
