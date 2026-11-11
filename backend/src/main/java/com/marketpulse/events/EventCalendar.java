package com.marketpulse.events;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * In-memory calendar of upcoming market events. Seeded from configuration; results dates found in news are
 * added at runtime (and lost on restart - a database arrives with signal tracking in a later phase).
 */
public class EventCalendar {

    /** Two results dates for the same company closer than this are the same quarter (e.g. a reschedule). */
    private static final int SAME_QUARTER_DAYS = 30;

    private final List<MarketEvent> events = new ArrayList<>();

    public EventCalendar(List<MarketEvent> seed) {
        seed.forEach(this::add);
    }

    public synchronized void add(MarketEvent event) {
        if (event.type() == EventType.RESULTS) {
            Optional<MarketEvent> sameQuarter = events.stream()
                    .filter(e -> e.type() == EventType.RESULTS && e.symbol().equals(event.symbol()))
                    .filter(e -> Math.abs(ChronoUnit.DAYS.between(e.date(), event.date())) <= SAME_QUARTER_DAYS)
                    .findFirst();
            if (sameQuarter.isPresent()) {
                MarketEvent existing = sameQuarter.get();
                if (existing.source() == Source.CONFIG && event.source() == Source.NEWS) {
                    return; // the manually entered date wins
                }
                events.remove(existing);
            }
        } else if (events.contains(event)) {
            return;
        }
        events.add(event);
    }

    /** Events from {@code from} up to and including {@code from + days}, earliest first. */
    public synchronized List<MarketEvent> upcoming(LocalDate from, int days) {
        LocalDate to = from.plusDays(days);
        return events.stream()
                .filter(e -> !e.date().isBefore(from) && !e.date().isAfter(to))
                .sorted(Comparator.comparing(MarketEvent::date))
                .toList();
    }

    public synchronized Optional<MarketEvent> nextResults(String symbol, LocalDate from, int days) {
        String normalized = symbol.toUpperCase(Locale.ROOT);
        return upcoming(from, days).stream()
                .filter(e -> e.type() == EventType.RESULTS && e.symbol().equals(normalized))
                .findFirst();
    }
}
