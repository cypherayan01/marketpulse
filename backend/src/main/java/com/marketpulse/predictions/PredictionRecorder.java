package com.marketpulse.predictions;

import com.marketpulse.market.Candle;
import com.marketpulse.market.MarketDataClient;
import com.marketpulse.news.Direction;
import com.marketpulse.reports.NewsEntry;
import com.marketpulse.reports.ReportStore;
import com.marketpulse.reports.ScanReport;
import com.marketpulse.reports.Section;
import com.marketpulse.signals.TradeSignal;
import com.marketpulse.universe.Segment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Listens to saved scans and records every call - setups to buy and stocks to avoid - as a LIVE prediction,
 * with that day's Nifty level and what the scored news said about the stock (recorded, not used in the score).
 */
public class PredictionRecorder implements Consumer<ScanReport> {

    private static final Logger log = LoggerFactory.getLogger(PredictionRecorder.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int NEWS_DAYS = 3;

    private final PredictionRepository repository;
    private final MarketDataClient market;
    private final String indexSymbol;
    private final Function<String, Segment> segments;
    private final ReportStore store;

    public PredictionRecorder(PredictionRepository repository, MarketDataClient market, String indexSymbol,
                              Function<String, Segment> segments, ReportStore store) {
        this.repository = repository;
        this.market = market;
        this.indexSymbol = indexSymbol;
        this.segments = segments;
        this.store = store;
    }

    @Override
    public void accept(ScanReport report) {
        double nifty = niftyClose(report.session());
        List<NewsEntry> news = store.recentNews();
        List<TradeSignal> buys = new ArrayList<>(report.bullish());
        List<TradeSignal> avoids = new ArrayList<>(report.bearish());
        for (Section section : report.sections()) {
            buys.addAll(section.bullish());
            avoids.addAll(section.bearish());
        }
        int saved = 0;
        for (TradeSignal s : buys) {
            saved += save(report, s, Call.BUY, nifty, news) ? 1 : 0;
        }
        for (TradeSignal s : avoids) {
            saved += save(report, s, Call.AVOID, nifty, news) ? 1 : 0;
        }
        log.info("Recorded {} new {} predictions", saved, report.style());
    }

    private boolean save(ScanReport report, TradeSignal s, Call call, double nifty, List<NewsEntry> news) {
        return repository.saveIfNew(Prediction.of(PredictionSource.LIVE, report.style(), s.symbol(),
                segments.apply(s.symbol()), report.session(), s.score(), s.classification(), call, s.close(), nifty,
                NewsSentiment.of(s.symbol(), report.session(), news), report.generatedAt()));
    }

    private double niftyClose(LocalDate session) {
        try {
            List<Candle> candles = market.dailyCandles(indexSymbol);
            return candles.stream().filter(c -> c.date().equals(session)).findFirst()
                    .orElse(candles.getLast()).close();
        } catch (RuntimeException e) {
            log.warn("No Nifty level for predictions: {}", e.getMessage());
            return 0; // outcomes are measured against Nifty's own candles later anyway
        }
    }

    /** Net direction of scored news naming the stock in the few days up to the signal: +1, -1 or 0. */
    static final class NewsSentiment {
        private NewsSentiment() {
        }

        static int of(String symbol, LocalDate session, List<NewsEntry> news) {
            Instant until = session.plusDays(1).atStartOfDay(IST).toInstant();
            Instant since = session.minusDays(NEWS_DAYS).atStartOfDay(IST).toInstant();
            int net = 0;
            for (NewsEntry n : news) {
                if (!n.stocks().contains(symbol) || n.publishedAt().isBefore(since) || !n.publishedAt().isBefore(until)) {
                    continue;
                }
                if (n.direction() == Direction.BULLISH) net += n.impactScore();
                if (n.direction() == Direction.BEARISH) net -= n.impactScore();
            }
            return Integer.signum(net);
        }
    }
}
