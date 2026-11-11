package com.marketpulse.market;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Free daily candles from Yahoo Finance's chart endpoint. Unofficial and unauthenticated, so it can change
 * or rate-limit - it sits behind {@link MarketDataClient} so a broker API can replace it later.
 */
public class YahooChartClient implements MarketDataClient {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final RestClient client;

    public YahooChartClient(RestClient.Builder builder) {
        this.client = builder.baseUrl("https://query1.finance.yahoo.com").build();
    }

    @Override
    public List<Candle> dailyCandles(String symbol) {
        ChartResponse response = client.get()
                .uri("/v8/finance/chart/{symbol}?range=2y&interval=1d", nse(symbol))
                .retrieve()
                .body(ChartResponse.class);
        if (response == null || response.chart() == null || response.chart().result() == null
                || response.chart().result().isEmpty()) {
            throw new IllegalStateException("No chart data for " + symbol);
        }
        return toCandles(response.chart().result().getFirst());
    }

    /** NSE stocks carry a ".NS" suffix on Yahoo; indices (starting with ^) do not. */
    static String nse(String symbol) {
        return symbol.startsWith("^") || symbol.contains(".") ? symbol : symbol + ".NS";
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
