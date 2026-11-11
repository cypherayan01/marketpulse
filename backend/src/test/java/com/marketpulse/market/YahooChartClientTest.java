package com.marketpulse.market;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

class YahooChartClientTest {

    @Test
    void fetchesDailyCandlesSkippingEmptyDaysAndUsingIstDates() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/M%26M.NS?range=2y&interval=1d"))
                .andRespond(withSuccess(new ClassPathResource("yahoo/chart-sample.json"), MediaType.APPLICATION_JSON));

        List<Candle> candles = new YahooChartClient(builder).dailyCandles("M&M");

        server.verify();
        assertThat(candles).hasSize(2); // the all-null day is skipped
        // 1794282300 = 2026-11-10 03:45 UTC = 09:15 IST
        assertThat(candles.getFirst()).isEqualTo(
                new Candle(LocalDate.of(2026, 11, 10), 3400.0, 3420.0, 3390.0, 3410.0, 1_200_000));
        assertThat(candles.getLast().date()).isEqualTo(LocalDate.of(2026, 11, 12));
        assertThat(candles.getLast().close()).isEqualTo(3465.0);
    }

    @Test
    void mapsNseSymbolToYahooSymbol() {
        assertThat(YahooChartClient.nse("RELIANCE")).isEqualTo("RELIANCE.NS");
        assertThat(YahooChartClient.nse("BAJAJ-AUTO")).isEqualTo("BAJAJ-AUTO.NS");
        assertThat(YahooChartClient.nse("^NSEI")).isEqualTo("^NSEI");       // index
        assertThat(YahooChartClient.nse("BZ=F")).isEqualTo("BZ=F");         // futures
        assertThat(YahooChartClient.nse("INR=X")).isEqualTo("INR=X");       // currency
        assertThat(YahooChartClient.nse("DX-Y.NYB")).isEqualTo("DX-Y.NYB"); // already exchange-qualified
    }

    @Test
    void retriesWithBackoffWhenRateLimited() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String url = "https://query1.finance.yahoo.com/v8/finance/chart/M%26M.NS?range=2y&interval=1d";
        server.expect(requestTo(url)).andRespond(withTooManyRequests());
        server.expect(requestTo(url))
                .andRespond(withSuccess(new ClassPathResource("yahoo/chart-sample.json"), MediaType.APPLICATION_JSON));
        List<Duration> sleeps = new ArrayList<>();

        List<Candle> candles = new YahooChartClient(builder, sleeps::add, Duration.ZERO).dailyCandles("M&M");

        server.verify();
        assertThat(candles).hasSize(2);
        assertThat(sleeps).containsExactly(Duration.ofSeconds(2));
    }

    @Test
    void givesUpAfterRepeatedRateLimits() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(times(3), requestTo("https://query1.finance.yahoo.com/v8/finance/chart/TCS.NS?range=2y&interval=1d"))
                .andRespond(withTooManyRequests());
        List<Duration> sleeps = new ArrayList<>();

        assertThatThrownBy(() -> new YahooChartClient(builder, sleeps::add, Duration.ZERO).dailyCandles("TCS"))
                .hasMessageContaining("rate limit");
        assertThat(sleeps).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(5));
    }

    @Test
    void spacesOutConsecutiveRequests() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(times(2), requestTo(org.hamcrest.Matchers.startsWith("https://query1.finance.yahoo.com/")))
                .andRespond(withSuccess(new ClassPathResource("yahoo/chart-sample.json"), MediaType.APPLICATION_JSON));
        List<Duration> sleeps = new ArrayList<>();
        YahooChartClient client = new YahooChartClient(builder, sleeps::add, Duration.ofMillis(300));

        client.dailyCandles("TCS");
        client.dailyCandles("INFY");

        assertThat(sleeps).hasSize(1);
        assertThat(sleeps.getFirst()).isGreaterThan(Duration.ofMillis(200)).isLessThanOrEqualTo(Duration.ofMillis(300));
    }
}
