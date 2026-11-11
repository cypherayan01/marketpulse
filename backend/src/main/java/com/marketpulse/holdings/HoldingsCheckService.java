package com.marketpulse.holdings;

import com.marketpulse.alerts.AlertDispatcher;
import com.marketpulse.events.EventCalendar;
import com.marketpulse.events.MarketEvent;
import com.marketpulse.market.Candle;
import com.marketpulse.market.MarketDataClient;
import com.marketpulse.positional.PositionalCalculator;
import com.marketpulse.reports.HoldingsReport;
import com.marketpulse.reports.ReportStore;
import com.marketpulse.swing.IndicatorCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** After the close: a HOLD / REVIEW / EXIT verdict for every stock you own. */
public class HoldingsCheckService {

    private static final Logger log = LoggerFactory.getLogger(HoldingsCheckService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final HoldingRepository holdings;
    private final MarketDataClient market;
    private final HoldingEvaluator evaluator;
    private final EventCalendar calendar;
    private final AlertDispatcher dispatcher;
    private final ReportStore store;
    private final Clock clock;
    private final String indexSymbol;

    public HoldingsCheckService(HoldingRepository holdings, MarketDataClient market, HoldingEvaluator evaluator,
                                EventCalendar calendar, AlertDispatcher dispatcher, ReportStore store, Clock clock,
                                String indexSymbol) {
        this.holdings = holdings;
        this.market = market;
        this.evaluator = evaluator;
        this.calendar = calendar;
        this.dispatcher = dispatcher;
        this.store = store;
        this.clock = clock;
        this.indexSymbol = indexSymbol;
    }

    /** @param force run even without a session today */
    public synchronized HoldingsRunSummary run(boolean force) {
        List<Holding> owned = holdings.findAll();
        if (owned.isEmpty()) {
            return HoldingsRunSummary.skipped("No holdings - add them on the dashboard");
        }
        List<Candle> index;
        try {
            index = market.dailyCandles(indexSymbol);
        } catch (RuntimeException e) {
            return HoldingsRunSummary.skipped("Index data unavailable: " + e.getMessage());
        }
        LocalDate today = LocalDate.now(clock.withZone(IST));
        if (!force && !index.getLast().date().equals(today)) {
            return HoldingsRunSummary.skipped("Market closed today");
        }
        MarketReturns nifty = new MarketReturns(
                new IndicatorCalculator().calculate(indexSymbol, index).return3m(),
                new PositionalCalculator().calculate(indexSymbol, index).return6m());

        Map<String, List<Candle>> candles = new HashMap<>();
        List<HoldingCheck> checks = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (Holding h : owned) {
            try {
                List<Candle> history = candles.computeIfAbsent(h.symbol(), market::dailyCandles);
                LocalDate results = calendar.nextResults(h.symbol(), today, HoldingEvaluator.RESULTS_WARNING_DAYS)
                        .map(MarketEvent::date).orElse(null);
                checks.add(evaluator.evaluate(h, history, nifty, results));
            } catch (RuntimeException e) {
                log.warn("Could not check holding {}: {}", h.symbol(), e.getMessage());
                failed.add(h.symbol());
            }
        }
        checks.sort(Comparator.comparing(HoldingCheck::verdict).thenComparing(c -> c.holding().symbol()));

        store.saveHoldings(new HoldingsReport(clock.instant(), index.getLast().date(), checks, failed));
        int exits = count(checks, Verdict.EXIT);
        int reviews = count(checks, Verdict.REVIEW);
        int holds = count(checks, Verdict.HOLD);
        boolean sent = dispatcher.send(
                "💼 Holdings check · %s · %d exit, %d review, %d hold".formatted(DAY.format(index.getLast().date()),
                        exits, reviews, holds),
                HoldingsFormatter.body(checks, failed));

        HoldingsRunSummary summary = new HoldingsRunSummary(checks.size(), exits, reviews, holds, failed.size(), sent, null);
        log.info("Holdings check finished: {}", summary);
        return summary;
    }

    private static int count(List<HoldingCheck> checks, Verdict verdict) {
        return (int) checks.stream().filter(c -> c.verdict() == verdict).count();
    }
}
