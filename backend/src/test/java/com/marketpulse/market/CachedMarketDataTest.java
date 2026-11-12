package com.marketpulse.market;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CachedMarketDataTest {

    private final List<String> calls = new ArrayList<>();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-12T11:00:00Z"));
    private boolean fail;
    private final MarketDataClient source = symbol -> {
        calls.add(symbol);
        if (fail) throw new IllegalStateException("Yahoo down");
        return TestCandles.trend(5, 100, 0.01, 1, LocalDate.of(2026, 11, 12));
    };
    private final CachedMarketData cached = new CachedMarketData(source, Duration.ofMinutes(20), clock);

    @Test
    void servesRepeatedRequestsFromTheCache() {
        cached.dailyCandles("TCS");
        cached.dailyCandles("TCS");
        cached.dailyCandles("INFY");

        assertThat(calls).containsExactly("TCS", "INFY");
    }

    @Test
    void fetchesAgainAfterTheCacheExpires() {
        cached.dailyCandles("TCS");
        clock.advance(Duration.ofMinutes(21));

        cached.dailyCandles("TCS");

        assertThat(calls).containsExactly("TCS", "TCS");
    }

    @Test
    void doesNotCacheFailures() {
        fail = true;
        assertThatThrownBy(() -> cached.dailyCandles("TCS")).hasMessageContaining("Yahoo down");
        fail = false;

        assertThat(cached.dailyCandles("TCS")).hasSize(5);
        assertThat(calls).containsExactly("TCS", "TCS");
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) { now = start; }

        void advance(Duration d) { now = now.plus(d); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
