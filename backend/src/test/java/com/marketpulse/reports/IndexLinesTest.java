package com.marketpulse.reports;

import com.marketpulse.market.Candle;
import com.marketpulse.market.MarketDataClient;
import com.marketpulse.market.Quote;
import com.marketpulse.market.TestCandles;
import com.marketpulse.universe.Segment;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class IndexLinesTest {

    @Test
    void prefersTheLiveQuoteForTodaysMove() {
        // Yahoo has no daily history for Nifty Midcap 100 - only a quote
        MarketDataClient market = new MarketDataClient() {
            @Override
            public List<Candle> dailyCandles(String symbol) {
                return List.of(new Candle(LocalDate.of(2026, 11, 11), 1, 1, 1, 61519.1, 0));
            }

            @Override
            public Optional<Quote> quote(String symbol) {
                return Optional.of(new Quote(61519.1, 61022.55));
            }
        };

        assertThat(IndexLines.today(market, List.of(Segment.MID)))
                .containsEntry(Segment.MID, "Nifty Midcap 100 +0.81% today");
    }

    @Test
    void fallsBackToDailyCandlesWithoutAQuote() {
        MarketDataClient market = symbol -> TestCandles.trend(10, 100, 0.01, 0, LocalDate.of(2026, 11, 11));

        assertThat(IndexLines.today(market, List.of(Segment.LARGE)))
                .containsEntry(Segment.LARGE, "Nifty 100 +1.00% today");
    }

    @Test
    void leavesOutSegmentsWithoutData() {
        MarketDataClient market = symbol -> { throw new IllegalStateException("no data"); };

        assertThat(IndexLines.today(market, List.of(Segment.SMALL))).isEmpty();
    }
}
