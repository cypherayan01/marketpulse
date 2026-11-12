package com.marketpulse.analysis;

import com.marketpulse.events.EventCalendar;
import com.marketpulse.events.MarketEvent;
import com.marketpulse.events.Source;
import com.marketpulse.holdings.Verdict;
import com.marketpulse.market.Candle;
import com.marketpulse.market.MarketDataClient;
import com.marketpulse.market.TestCandles;
import com.marketpulse.news.Direction;
import com.marketpulse.news.NewsImpact;
import com.marketpulse.news.NewsItem;
import com.marketpulse.reports.ReportStore;
import com.marketpulse.signals.TradeStyle;
import com.marketpulse.universe.IndexMember;
import com.marketpulse.universe.Segment;
import com.marketpulse.universe.Universe;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockAnalyzerTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);
    private final Clock clock = Clock.fixed(TODAY.atTime(17, 0).atZone(IST).toInstant(), IST);

    private final Map<String, List<Candle>> data = new HashMap<>();
    private final MarketDataClient market = symbol -> {
        List<Candle> c = data.get(symbol);
        if (c == null) throw new IllegalStateException("no data for " + symbol);
        return c;
    };
    private final Universe universe = Universe.of(
            List.of(new IndexMember("UPCO", "Up Company Ltd.", "Information Technology")), List.of(), List.of(),
            List.of(new IndexMember("UPCO", "Up Company Ltd.", "Information Technology")));
    private final ReportStore store = new ReportStore(50);
    private EventCalendar calendar = new EventCalendar(List.of());

    {
        data.put("^NSEI", TestCandles.trend(600, 20000, 0.0003, 0, TODAY));
    }

    @Test
    void strongUptrendOnBothHorizonsIsABuy() {
        data.put("UPCO", TestCandles.trend(600, 100, 0.002, 1_000, TODAY));

        StockAnalysis a = analyzer().analyze("upco", Optional.empty());

        assertThat(a.symbol()).isEqualTo("UPCO");
        assertThat(a.name()).isEqualTo("Up Company Ltd.");
        assertThat(a.segment()).isEqualTo(Segment.LARGE);
        assertThat(a.industry()).isEqualTo("Information Technology");
        assertThat(a.verdict()).isEqualTo(AnalysisVerdict.BUY);
        assertThat(a.swing().style()).isEqualTo(TradeStyle.SWING);
        assertThat(a.positional().style()).isEqualTo(TradeStyle.POSITIONAL);
        assertThat(a.headline()).contains("short-term and long-term");
        assertThat(a.holding()).isNull();
    }

    @Test
    void downtrendIsAvoidAndExplainsWhatSellMeans() {
        data.put("DOWNCO", TestCandles.trend(600, 100, -0.002, 1_000, TODAY));

        StockAnalysis a = analyzer().analyze("DOWNCO", Optional.empty());

        assertThat(a.verdict()).isEqualTo(AnalysisVerdict.AVOID);
        assertThat(a.headline()).contains("exit if you hold");
        assertThat(a.segment()).isNull(); // not in Nifty 500
    }

    @Test
    void recentListingGetsASwingViewOnly() {
        data.put("NEWIPO", TestCandles.trend(150, 100, 0.004, 1_000, TODAY)); // ~7 months: too short for weekly view

        StockAnalysis a = analyzer().analyze("NEWIPO", Optional.empty());

        assertThat(a.positional()).isNull();
        assertThat(a.positionalUnavailable()).contains("weeks");
        assertThat(a.verdict()).isEqualTo(AnalysisVerdict.BUY_SWING);
    }

    @Test
    void ifYouOwnItSaysWhetherToKeepIt() {
        data.put("DOWNCO", TestCandles.trend(600, 100, -0.002, 1_000, TODAY));
        double price = data.get("DOWNCO").getLast().close();

        StockAnalysis a = analyzer().analyze("DOWNCO",
                Optional.of(new OwnedPosition(10, price * 1.3, TODAY.minusDays(90), TradeStyle.SWING, null)));

        assertThat(a.holding()).isNotNull();
        assertThat(a.holding().verdict()).isEqualTo(Verdict.EXIT); // trailing stop from the highest close since buying
        assertThat(a.holding().pnlPct()).isNegative();
    }

    @Test
    void mentionsResultsDueSoonAndRecentNews() {
        data.put("UPCO", TestCandles.trend(600, 100, 0.002, 1_000, TODAY));
        calendar = new EventCalendar(List.of(MarketEvent.results(TODAY.plusDays(4), "UPCO", Source.CONFIG)));
        store.addNews(news("1", "Up Company wins large order", List.of("UPCO")), true, Instant.EPOCH);
        store.addNews(news("2", "Crude oil slips", List.of()), false, Instant.EPOCH);

        StockAnalysis a = analyzer().analyze("UPCO", Optional.empty());

        assertThat(a.resultsDate()).isEqualTo(TODAY.plusDays(4));
        assertThat(a.reasons()).anyMatch(r -> r.contains("Results on 16 Nov"));
        assertThat(a.news()).extracting(n -> n.title()).containsExactly("Up Company wins large order");
    }

    @Test
    void unknownSymbolIsReported() {
        assertThatThrownBy(() -> analyzer().analyze("NOSUCH", Optional.empty()))
                .isInstanceOf(UnknownStockException.class)
                .hasMessageContaining("NOSUCH");
    }

    private StockAnalyzer analyzer() {
        return new StockAnalyzer(market, universe, calendar, store, clock, "^NSEI");
    }

    private static NewsImpact news(String id, String title, List<String> stocks) {
        return new NewsImpact(new NewsItem(id, title, "", "https://x/" + id, "ET", Instant.EPOCH), 6, Direction.BULLISH,
                List.of(), stocks, "why");
    }
}
