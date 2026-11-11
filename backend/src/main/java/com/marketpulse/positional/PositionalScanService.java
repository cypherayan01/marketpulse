package com.marketpulse.positional;

import com.marketpulse.alerts.AlertDispatcher;
import com.marketpulse.events.EventCalendar;
import com.marketpulse.events.MarketEvent;
import com.marketpulse.market.MarketDataClient;
import com.marketpulse.reports.ReportStore;
import com.marketpulse.reports.ScanReport;
import com.marketpulse.signals.TradeStyle;
import com.marketpulse.signals.TradeSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Weekend scan of the weekly chart for positional (1-6 month) setups. */
public class PositionalScanService {

    private static final Logger log = LoggerFactory.getLogger(PositionalScanService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int EVENT_DAYS = 7;

    private final MarketDataClient market;
    private final PositionalCalculator calculator;
    private final PositionalScorer scorer;
    private final AlertDispatcher dispatcher;
    private final EventCalendar calendar;
    private final Clock clock;
    private final PositionalSettings settings;
    private final ReportStore store;

    public PositionalScanService(MarketDataClient market, PositionalCalculator calculator, PositionalScorer scorer,
                                 AlertDispatcher dispatcher, EventCalendar calendar, Clock clock,
                                 PositionalSettings settings, ReportStore store) {
        this.market = market;
        this.calculator = calculator;
        this.scorer = scorer;
        this.dispatcher = dispatcher;
        this.calendar = calendar;
        this.clock = clock;
        this.settings = settings;
        this.store = store;
    }

    public synchronized PositionalRunSummary run() {
        PositionalSnapshot index;
        try {
            index = calculator.calculate(settings.indexSymbol(), market.dailyCandles(settings.indexSymbol()));
        } catch (RuntimeException e) {
            log.warn("Index data unavailable, skipping positional scan: {}", e.getMessage());
            return new PositionalRunSummary(0, 0, 0, 0, false);
        }

        List<TradeSignal> signals = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (String symbol : settings.universe()) {
            try {
                signals.add(scorer.score(calculator.calculate(symbol, market.dailyCandles(symbol)), index.return6m()));
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

        LocalDate today = LocalDate.now(clock.withZone(IST));
        Map<String, MarketEvent> resultsSoon = new LinkedHashMap<>();
        for (TradeSignal s : bullish) {
            calendar.nextResults(s.symbol(), today, settings.resultsWarningDays())
                    .ifPresent(e -> resultsSoon.put(s.symbol(), e));
        }
        List<MarketEvent> events = calendar.upcoming(today.plusDays(1), EVENT_DAYS);

        Map<String, LocalDate> resultsDates = new LinkedHashMap<>();
        resultsSoon.forEach((symbol, event) -> resultsDates.put(symbol, event.date()));
        store.saveScan(new ScanReport(TradeStyle.POSITIONAL, clock.instant(), index.weekEnding(),
                PositionalReportFormatter.marketLine(index), bullish, bearish, resultsDates, events, signals.size(),
                failed, List.of()));

        boolean sent = dispatcher.send(
                PositionalReportFormatter.subject(index.weekEnding(), bullish.size(), bearish.size()),
                PositionalReportFormatter.body(index, bullish, bearish, resultsSoon, events, today, signals.size(), failed));

        PositionalRunSummary summary = new PositionalRunSummary(signals.size(), failed.size(), bullish.size(),
                bearish.size(), sent);
        log.info("Positional scan finished: {}", summary);
        return summary;
    }
}
