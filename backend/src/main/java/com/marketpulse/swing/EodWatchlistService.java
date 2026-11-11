package com.marketpulse.swing;

import com.marketpulse.signals.TradeSignal;

import com.marketpulse.alerts.AlertDispatcher;
import com.marketpulse.events.EventCalendar;
import com.marketpulse.events.MarketEvent;
import com.marketpulse.market.Candle;
import com.marketpulse.market.MarketDataClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** After the close: scan the universe, rank swing setups, and send the watchlist report. */
public class EodWatchlistService {

    private static final Logger log = LoggerFactory.getLogger(EodWatchlistService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final int EVENT_DAYS = 7;

    private final MarketDataClient market;
    private final IndicatorCalculator calculator;
    private final SwingScorer scorer;
    private final AlertDispatcher dispatcher;
    private final EventCalendar calendar;
    private final Clock clock;
    private final SwingSettings settings;

    public EodWatchlistService(MarketDataClient market, IndicatorCalculator calculator, SwingScorer scorer,
                               AlertDispatcher dispatcher, EventCalendar calendar, Clock clock,
                               SwingSettings settings) {
        this.market = market;
        this.calculator = calculator;
        this.scorer = scorer;
        this.dispatcher = dispatcher;
        this.calendar = calendar;
        this.clock = clock;
        this.settings = settings;
    }

    /** @param force run even if there was no trading session today (useful for testing on weekends) */
    public synchronized EodRunSummary run(boolean force) {
        List<Candle> index;
        try {
            index = market.dailyCandles(settings.indexSymbol());
        } catch (RuntimeException e) {
            log.warn("Index data unavailable, skipping EOD scan: {}", e.getMessage());
            return EodRunSummary.skipped("Index data unavailable: " + e.getMessage());
        }
        LocalDate today = LocalDate.now(clock.withZone(IST));
        LocalDate lastSession = index.getLast().date();
        if (!force && !lastSession.equals(today)) {
            String reason = "Market closed today (last session %s)".formatted(DAY.format(lastSession));
            log.info("Skipping EOD scan: {}", reason);
            return EodRunSummary.skipped(reason);
        }

        IndicatorSnapshot indexSnapshot = calculator.calculate(settings.indexSymbol(), index);
        List<TradeSignal> signals = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (String symbol : settings.universe()) {
            try {
                signals.add(scorer.score(calculator.calculate(symbol, market.dailyCandles(symbol)), indexSnapshot.return3m()));
            } catch (RuntimeException e) {
                log.warn("Skipping {}: {}", symbol, e.getMessage());
                failed.add(symbol);
            }
        }

        List<TradeSignal> bullish = signals.stream()
                .filter(s -> s.classification().bullish())
                .sorted(Comparator.comparingInt(TradeSignal::score).reversed())
                .limit(settings.topN())
                .toList();
        List<TradeSignal> bearish = signals.stream()
                .filter(s -> s.classification().bearish())
                .sorted(Comparator.comparingInt(TradeSignal::score))
                .limit(settings.topN())
                .toList();

        Map<String, MarketEvent> resultsSoon = new LinkedHashMap<>();
        for (TradeSignal s : bullish) {
            calendar.nextResults(s.symbol(), today, settings.resultsWarningDays())
                    .ifPresent(e -> resultsSoon.put(s.symbol(), e));
        }
        List<MarketEvent> events = calendar.upcoming(today.plusDays(1), EVENT_DAYS);

        MarketContext context = MarketContext.of(index, indexSnapshot);
        boolean sent = dispatcher.send(
                SwingReportFormatter.subject(lastSession, bullish.size(), bearish.size()),
                SwingReportFormatter.body(context, bullish, bearish, resultsSoon, events, today, signals.size(), failed));

        EodRunSummary summary = new EodRunSummary(signals.size(), failed.size(), bullish.size(), bearish.size(), sent, null);
        log.info("EOD scan finished: {}", summary);
        return summary;
    }

    /** Benchmark state shown at the top of the report. */
    record MarketContext(double close, double dayChangePct, boolean aboveEma50, boolean aboveEma200) {
        static MarketContext of(List<Candle> index, IndicatorSnapshot snapshot) {
            double prev = index.get(index.size() - 2).close();
            return new MarketContext(snapshot.close(), (snapshot.close() / prev - 1) * 100,
                    snapshot.close() > snapshot.ema50(), snapshot.close() > snapshot.ema200());
        }
    }
}
