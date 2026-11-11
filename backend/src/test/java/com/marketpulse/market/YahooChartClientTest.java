package com.marketpulse.market;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
        assertThat(YahooChartClient.nse("^NSEI")).isEqualTo("^NSEI");
    }
}
