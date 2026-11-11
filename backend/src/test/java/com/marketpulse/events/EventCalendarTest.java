package com.marketpulse.events;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventCalendarTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

    @Test
    void returnsUpcomingEventsInWindowSortedByDate() {
        EventCalendar calendar = new EventCalendar(List.of(
                MarketEvent.macro(LocalDate.of(2026, 12, 9), "Fed decision", Impact.HIGH),
                MarketEvent.macro(LocalDate.of(2026, 12, 4), "RBI policy decision", Impact.HIGH),
                MarketEvent.macro(LocalDate.of(2026, 11, 1), "Past event", Impact.LOW),
                MarketEvent.macro(LocalDate.of(2027, 2, 5), "Far future", Impact.HIGH)));

        List<MarketEvent> upcoming = calendar.upcoming(TODAY, 30);

        assertThat(upcoming).extracting(MarketEvent::title).containsExactly("RBI policy decision", "Fed decision");
    }

    @Test
    void findsNextResultsForSymbolWithinHoldingWindow() {
        EventCalendar calendar = new EventCalendar(List.of(
                MarketEvent.results(LocalDate.of(2026, 11, 20), "TCS", Source.CONFIG)));

        assertThat(calendar.nextResults("TCS", TODAY, 15)).map(MarketEvent::date).contains(LocalDate.of(2026, 11, 20));
        assertThat(calendar.nextResults("TCS", TODAY, 5)).isEmpty();
        assertThat(calendar.nextResults("INFY", TODAY, 15)).isEmpty();
    }

    @Test
    void newsDateForSameQuarterReplacesEarlierNewsDate() {
        EventCalendar calendar = new EventCalendar(List.of());
        calendar.add(MarketEvent.results(LocalDate.of(2026, 11, 20), "TCS", Source.NEWS));

        calendar.add(MarketEvent.results(LocalDate.of(2026, 11, 24), "TCS", Source.NEWS)); // rescheduled

        assertThat(calendar.upcoming(TODAY, 60)).extracting(MarketEvent::date)
                .containsExactly(LocalDate.of(2026, 11, 24));
    }

    @Test
    void manualDateWinsOverNewsDate() {
        EventCalendar calendar = new EventCalendar(List.of(
                MarketEvent.results(LocalDate.of(2026, 11, 20), "TCS", Source.CONFIG)));

        calendar.add(MarketEvent.results(LocalDate.of(2026, 11, 22), "TCS", Source.NEWS));

        assertThat(calendar.upcoming(TODAY, 60)).extracting(MarketEvent::date)
                .containsExactly(LocalDate.of(2026, 11, 20));
    }

    @Test
    void differentQuartersAreKeptSeparately() {
        EventCalendar calendar = new EventCalendar(List.of());
        calendar.add(MarketEvent.results(LocalDate.of(2026, 11, 20), "TCS", Source.NEWS));

        calendar.add(MarketEvent.results(LocalDate.of(2027, 1, 15), "TCS", Source.NEWS));

        assertThat(calendar.upcoming(TODAY, 90)).hasSize(2);
    }

    @Test
    void symbolsAreCaseInsensitive() {
        EventCalendar calendar = new EventCalendar(List.of());
        calendar.add(MarketEvent.results(LocalDate.of(2026, 11, 20), "tcs", Source.NEWS));

        assertThat(calendar.nextResults("TCS", TODAY, 15)).isPresent();
    }

    @Test
    void tellsTheListenerAboutNewlyAcceptedNewsResultsDatesOnly() {
        EventCalendar calendar = new EventCalendar(List.of(
                MarketEvent.results(LocalDate.of(2026, 11, 20), "TCS", Source.CONFIG)));
        java.util.List<MarketEvent> heard = new java.util.ArrayList<>();
        calendar.onNewsResults(heard::add);

        calendar.add(MarketEvent.results(LocalDate.of(2026, 11, 22), "TCS", Source.NEWS));   // loses to manual date
        calendar.add(MarketEvent.results(LocalDate.of(2026, 11, 25), "INFY", Source.NEWS));  // accepted
        calendar.add(MarketEvent.macro(LocalDate.of(2026, 12, 4), "RBI", Impact.HIGH));       // not news results

        assertThat(heard).extracting(MarketEvent::symbol).containsExactly("INFY");
    }
}
