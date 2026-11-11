package com.marketpulse.market;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Free daily candles from Yahoo Finance's chart endpoint. Unofficial and unauthenticated, so it can change
 * or rate-limit - it sits behind {@link MarketDataClient} so a broker API can replace it later.
 * Requests are spaced out and HTTP 429 responses are retried with backoff.
 */
public class YahooChartClient implements MarketDataClient {

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
        for (int attempt = 0; ; attempt++) {
            try {
                return toCandles(symbol, fetch(symbol));
            } catch (HttpClientErrorException.TooManyRequests e) {
                if (attempt >= RETRY_BACKOFF.size()) {
                    throw new IllegalStateException("Yahoo rate limit hit for " + symbol + ", giving up", e);
                }
                sleeper.sleep(RETRY_BACKOFF.get(attempt));
            }
        }
    }

    private synchronized ChartResponse fetch(String symbol) {
        long sinceLast = System.nanoTime() - lastRequestNanos;
        if (lastRequestNanos != 0 && sinceLast < minInterval.toNanos()) {
            sleeper.sleep(Duration.ofNanos(minInterval.toNanos() - sinceLast));
        }
        try {
            return client.get()
                    .uri("/v8/finance/chart/{symbol}?range=2y&interval=1d", nse(symbol))
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
        Quote q = result.indicators().quote().getFirst();
        for (int i = 0; i < result.timestamp().size(); i++) {
            Double open = q.open().get(i), high = q.high().get(i), low = q.low().get(i), close = q.close().get(i);
            if (open == null || high == null || low == null || close == null) {
                continue; // Yahoo leaves gaps as nulls
            }
            Long volume = q.volume().get(i);
            LocalDate date = Instant.ofEpochSecond(result.timestamp().get(i)).atZone(IST).toLocalDate();
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
    record Result(List<Long> timestamp, Indicators indicators) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Indicators(List<Quote> quote) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Quote(List<Double> open, List<Double> high, List<Double> low, List<Double> close, List<Long> volume) {
    }
}
