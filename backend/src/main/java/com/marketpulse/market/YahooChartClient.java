package com.marketpulse.market;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Free daily candles from Yahoo Finance's chart endpoint. Unofficial and unauthenticated, so it can change
 * or rate-limit - it sits behind {@link MarketDataClient} so a broker API can replace it later.
 * Requests are spaced out and HTTP 429 responses are retried with backoff.
 */
public class YahooChartClient implements MarketDataClient, IntradayDataClient {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final List<Duration> RETRY_BACKOFF = List.of(Duration.ofSeconds(2), Duration.ofSeconds(5));

    /** Pauses the calling thread; injectable so tests don't actually wait. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration);
    }

    private final RestClient client;
    private final Sleeper sleeper;
    private final Duration minInterval;
    private long lastRequestNanos;

    public YahooChartClient(RestClient.Builder builder) {
        this(builder, YahooChartClient::sleepQuietly, Duration.ofMillis(300));
    }

    public YahooChartClient(RestClient.Builder builder, Sleeper sleeper, Duration minInterval) {
        this.client = builder.baseUrl("https://query1.finance.yahoo.com").build();
        this.sleeper = sleeper;
        this.minInterval = minInterval;
    }

    @Override
    public List<Candle> dailyCandles(String symbol) {
        return toCandles(symbol, fetchWithRetry(symbol, "2y", "1d"));
    }

    /** From the 1-day chart's metadata: works even for indices Yahoo keeps no daily history for. */
    @Override
    public Optional<Quote> quote(String symbol) {
        ChartResponse response = fetchWithRetry(symbol, "1d", "1d");
        if (response == null || response.chart() == null || response.chart().result() == null
                || response.chart().result().isEmpty()) {
            return Optional.empty();
        }
        Meta meta = response.chart().result().getFirst().meta();
        if (meta == null || meta.regularMarketPrice() == null || meta.chartPreviousClose() == null
                || meta.chartPreviousClose() == 0) {
            return Optional.empty();
        }
        return Optional.of(new Quote(meta.regularMarketPrice(), meta.chartPreviousClose()));
    }

    @Override
    public List<IntradayCandle> intradayCandles(String symbol) {
        ChartResponse response = fetchWithRetry(symbol, "1d", "15m");
        if (response == null || response.chart() == null || response.chart().result() == null
                || response.chart().result().isEmpty()) {
            return List.of();
        }
        Result result = response.chart().result().getFirst();
        List<IntradayCandle> bars = new ArrayList<>();
        if (result.timestamp() == null || result.indicators() == null || result.indicators().quote() == null) {
            return bars;
        }
        QuoteBars q = result.indicators().quote().getFirst();
        for (int i = 0; i < result.timestamp().size(); i++) {
            Double open = q.open().get(i), high = q.high().get(i), low = q.low().get(i), close = q.close().get(i);
            if (open == null || high == null || low == null || close == null) {
                continue;
            }
            Long volume = q.volume().get(i);
            bars.add(new IntradayCandle(Instant.ofEpochSecond(result.timestamp().get(i)), open, high, low, close,
                    volume == null ? 0 : volume));
        }
        return bars;
    }

    private ChartResponse fetchWithRetry(String symbol, String range, String interval) {
        for (int attempt = 0; ; attempt++) {
            try {
                return fetch(symbol, range, interval);
            } catch (HttpClientErrorException.TooManyRequests e) {
                if (attempt >= RETRY_BACKOFF.size()) {
                    throw new IllegalStateException("Yahoo rate limit hit for " + symbol + ", giving up", e);
                }
                sleeper.sleep(RETRY_BACKOFF.get(attempt));
            } catch (ResourceAccessException e) { // network drop, timeout, connection reset
                if (attempt >= RETRY_BACKOFF.size()) {
                    throw new IllegalStateException("Network error fetching " + symbol + ": " + e.getMessage(), e);
                }
                sleeper.sleep(RETRY_BACKOFF.get(attempt));
            }
        }
    }

    private synchronized ChartResponse fetch(String symbol, String range, String interval) {
        long sinceLast = System.nanoTime() - lastRequestNanos;
        if (lastRequestNanos != 0 && sinceLast < minInterval.toNanos()) {
            sleeper.sleep(Duration.ofNanos(minInterval.toNanos() - sinceLast));
        }
        try {
            return client.get()
                    .uri("/v8/finance/chart/{symbol}?range={range}&interval={interval}", nse(symbol), range, interval)
                    .retrieve()
                    .body(ChartResponse.class);
        } finally {
            lastRequestNanos = System.nanoTime();
        }
    }

    private static List<Candle> toCandles(String symbol, ChartResponse response) {
        if (response == null || response.chart() == null || response.chart().result() == null
                || response.chart().result().isEmpty()) {
            throw new IllegalStateException("No chart data for " + symbol);
        }
        return toCandles(response.chart().result().getFirst());
    }

    /**
     * Yahoo sometimes blanks the latest daily bar after hours while its metadata still holds that day's price,
     * high, low and volume. Rebuild the bar from the metadata - but only if the metadata is from the same day.
     */
    private static Optional<Candle> completeFromMeta(LocalDate date, Double open, List<Candle> earlier, Meta meta) {
        if (meta == null || meta.regularMarketTime() == null || meta.regularMarketPrice() == null
                || meta.regularMarketDayHigh() == null || meta.regularMarketDayLow() == null) {
            return Optional.empty();
        }
        LocalDate metaDate = Instant.ofEpochSecond(meta.regularMarketTime()).atZone(IST).toLocalDate();
        if (!metaDate.equals(date)) {
            return Optional.empty();
        }
        double close = meta.regularMarketPrice();
        double o = open != null ? open : earlier.isEmpty() ? close : earlier.getLast().close();
        double high = Math.max(meta.regularMarketDayHigh(), Math.max(o, close));
        double low = Math.min(meta.regularMarketDayLow(), Math.min(o, close));
        long volume = meta.regularMarketVolume() == null ? 0 : meta.regularMarketVolume();
        return Optional.of(new Candle(date, o, high, low, close, volume));
    }

    private static void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Plain NSE stock symbols get Yahoo's ".NS" suffix; indices (^), futures/currencies (=) and qualified symbols (.) do not. */
    static String nse(String symbol) {
        return symbol.startsWith("^") || symbol.contains(".") || symbol.contains("=") ? symbol : symbol + ".NS";
    }

    private static List<Candle> toCandles(Result result) {
        List<Candle> candles = new ArrayList<>();
        if (result.timestamp() == null || result.indicators() == null || result.indicators().quote() == null) {
            return candles;
        }
        QuoteBars q = result.indicators().quote().getFirst();
        int last = result.timestamp().size() - 1;
        for (int i = 0; i <= last; i++) {
            Double open = q.open().get(i), high = q.high().get(i), low = q.low().get(i), close = q.close().get(i);
            LocalDate date = Instant.ofEpochSecond(result.timestamp().get(i)).atZone(IST).toLocalDate();
            if (open == null || high == null || low == null || close == null) {
                if (i == last) {
                    completeFromMeta(date, open, candles, result.meta()).ifPresent(candles::add);
                }
                continue; // Yahoo leaves gaps as nulls
            }
            Long volume = q.volume().get(i);
            candles.add(new Candle(date, open, high, low, close, volume == null ? 0 : volume));
        }
        return candles;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChartResponse(Chart chart) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Chart(List<Result> result) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result(Meta meta, List<Long> timestamp, Indicators indicators) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Meta(Long regularMarketTime, Double regularMarketPrice, Double regularMarketDayHigh,
                Double regularMarketDayLow, Long regularMarketVolume, Double chartPreviousClose) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Indicators(List<QuoteBars> quote) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record QuoteBars(List<Double> open, List<Double> high, List<Double> low, List<Double> close, List<Long> volume) {
    }
}
