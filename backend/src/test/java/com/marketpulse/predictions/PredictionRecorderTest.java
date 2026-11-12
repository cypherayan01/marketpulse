package com.marketpulse.predictions;

import com.marketpulse.market.Candle;
import com.marketpulse.market.MarketDataClient;
import com.marketpulse.market.TestCandles;
import com.marketpulse.news.Direction;
import com.marketpulse.news.NewsImpact;
import com.marketpulse.news.NewsItem;
import com.marketpulse.reports.ReportStore;
import com.marketpulse.reports.ScanReport;
import com.marketpulse.reports.Section;
import com.marketpulse.signals.Classification;
import com.marketpulse.signals.Confidence;
import com.marketpulse.signals.TradePlan;
import com.marketpulse.signals.TradeSignal;
import com.marketpulse.signals.TradeStyle;
import com.marketpulse.universe.Segment;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PredictionRecorderTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 11);
    private final InMemoryPredictions repository = new InMemoryPredictions();
    private final ReportStore store = new ReportStore(50);
    private final MarketDataClient market = symbol -> {
        List<Candle> nifty = new java.util.ArrayList<>(TestCandles.trend(5, 25000, 0.001, 0, DAY.minusDays(1)));
        nifty.add(new Candle(DAY, 25200, 25300, 25100, 25255.05, 0));
        return nifty;
    };
    private final PredictionRecorder recorder = new PredictionRecorder(repository, market, "^NSEI",
            symbol -> symbol.equals("SMALLCO") ? Segment.SMALL : Segment.LARGE, store);

    @Test
    void recordsEveryBuyAndAvoidCallWithTheDaysNiftyLevel() {
        recorder.accept(report(
                List.of(signal("HDFCBANK", 86, 806.6)), List.of(signal("NTPC", 21, 327)),
                new Section(Segment.SMALL, null, 100, List.of(signal("SMALLCO", 90, 412.5)), List.of())));

        assertThat(repository.rows).extracting(Prediction::symbol, Prediction::call, Prediction::segment)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("HDFCBANK", Call.BUY, Segment.LARGE),
                        org.assertj.core.groups.Tuple.tuple("NTPC", Call.AVOID, Segment.LARGE),
                        org.assertj.core.groups.Tuple.tuple("SMALLCO", Call.BUY, Segment.SMALL));
        Prediction hdfc = repository.rows.stream().filter(p -> p.symbol().equals("HDFCBANK")).findFirst().orElseThrow();
        assertThat(hdfc.price()).isEqualTo(806.6);
        assertThat(hdfc.niftyPrice()).isEqualTo(25255.05);
        assertThat(hdfc.signalDate()).isEqualTo(DAY);
        assertThat(hdfc.source()).isEqualTo(PredictionSource.LIVE);
        assertThat(hdfc.style()).isEqualTo(TradeStyle.SWING);
    }

    @Test
    void notesWhatTheNewsSaidAboutTheStockWithoutChangingTheScore() {
        store.addNews(news("1", List.of("HDFCBANK"), Direction.BULLISH, DAY), true, Instant.EPOCH);
        store.addNews(news("2", List.of("NTPC"), Direction.BEARISH, DAY.minusDays(1)), false, Instant.EPOCH);
        store.addNews(news("3", List.of("NTPC"), Direction.BULLISH, DAY.minusDays(10)), false, Instant.EPOCH); // too old

        recorder.accept(report(List.of(signal("HDFCBANK", 86, 806.6)), List.of(signal("NTPC", 21, 327))));

        assertThat(repository.rows).extracting(Prediction::symbol, Prediction::newsSentiment, Prediction::score)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("HDFCBANK", 1, 86),
                        org.assertj.core.groups.Tuple.tuple("NTPC", -1, 21));
    }

    @Test
    void aStockListedInTwoPlacesIsOnePrediction() {
        TradeSignal hdfc = signal("HDFCBANK", 86, 806.6);
        recorder.accept(report(List.of(hdfc), List.of(),
                new Section(Segment.LARGE, null, 100, List.of(hdfc), List.of())));

        assertThat(repository.rows).hasSize(1);
    }

    private static ScanReport report(List<TradeSignal> bullish, List<TradeSignal> bearish, Section... sections) {
        return new ScanReport(TradeStyle.SWING, Instant.parse("2026-11-11T11:00:00Z"), DAY, "Nifty", bullish, bearish,
                Map.of(), List.of(), 500, List.of(), List.of(), List.of(sections));
    }

    private static TradeSignal signal(String symbol, int score, double close) {
        boolean bull = score >= 65;
        return new TradeSignal(symbol, TradeStyle.SWING, score, Classification.of(score), Confidence.HIGH, List.of(),
                List.of(), bull ? new TradePlan(close, close * 0.95, close * 1.1, 5) : null, bull ? "invalid" : null, close);
    }

    private static NewsImpact news(String id, List<String> stocks, Direction direction, LocalDate day) {
        Instant at = day.atTime(10, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
        return new NewsImpact(new NewsItem(id, "headline " + id, "", "https://x/" + id, "ET", at), 6, direction,
                List.of(), stocks, "why");
    }
}
