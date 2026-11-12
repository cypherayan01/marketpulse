package com.marketpulse.market;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps daily candles for a short while so jobs that run back to back (swing scan → tracking → holdings)
 * fetch each of the ~500 stocks only once. Failures are not cached.
 */
public class CachedMarketData implements MarketDataClient {

    private record Entry(List<Candle> candles, Instant fetchedAt) {
    }

    private final MarketDataClient source;
    private final Duration ttl;
    private final Clock clock;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public CachedMarketData(MarketDataClient source, Duration ttl, Clock clock) {
        this.source = source;
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    public List<Candle> dailyCandles(String symbol) {
        Instant now = clock.instant();
        Entry entry = cache.get(symbol);
        if (entry != null && entry.fetchedAt().plus(ttl).isAfter(now)) {
            return entry.candles();
        }
        List<Candle> candles = List.copyOf(source.dailyCandles(symbol));
        cache.put(symbol, new Entry(candles, now));
        cache.values().removeIf(e -> !e.fetchedAt().plus(ttl).isAfter(now)); // drop stale entries
        return candles;
    }

    /** Quotes are live by nature - never cached. */
    @Override
    public Optional<Quote> quote(String symbol) {
        return source.quote(symbol);
    }
}
