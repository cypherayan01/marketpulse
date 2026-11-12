package com.marketpulse.swing;

import com.marketpulse.signals.TradeSignal;

import com.marketpulse.alerts.AlertDispatcher;
import com.marketpulse.events.EventCalendar;
import com.marketpulse.events.MarketEvent;
import com.marketpulse.market.Candle;
import com.marketpulse.market.MarketDataClient;
import com.marketpulse.reports.IndexLines;
import com.marketpulse.reports.ReportStore;
import com.marketpulse.reports.Section;
import com.marketpulse.reports.SectionBuilder;
import com.marketpulse.reports.ScanReport;
import com.marketpulse.signals.TradeStyle;
import com.marketpulse.universe.Segment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
    private final ReportStore store;

    private final Object runLock = new Object();

    public EodWatchlistService(MarketDataClient market, IndicatorCalculator calculator, SwingScorer scorer,
                               AlertDispatcher dispatcher, EventCalendar calendar, Clock clock,
                               SwingSettings settings, ReportStore store) {
        this.market = market;
        this.calculator = calculator;
        this.scorer = scorer;
        this.dispatcher = dispatcher;
        this.calendar = calendar;
        this.clock = clock;
        this.settings = settings;
        this.store = store;
    }

    /** @param force run even if there was no trading session today (useful for testing on weekends) */
    public EodRunSummary run(boolean force) {
        synchronized (runLock) { // private lock: outside code can never hold it
            return runLocked(force);
        }
    }

    private EodRunSummary runLocked(boolean force) {
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
        for (String symbol : settings.universe().symbols()) {
            try {
                signals.add(scorer.score(calculator.calculate(symbol, market.dailyCandles(symbol)), indexSnapshot.return3m()));
            } catch (RuntimeException e) {
                log.warn("Skipping {}: {}", symbol, e.getMessage());
                failed.add(symbol);
            }
        }

        List<Section> grouped = SectionBuilder.build(signals, settings.universe(), settings.topN(), Map.of());
        List<Section> sections = withIndexLines(grouped,
                IndexLines.today(market, grouped.stream().map(Section::segment).toList()));
        List<TradeSignal> bestOverall = SectionBuilder.topBullish(signals, settings.topN());
        List<TradeSignal> worstOverall = SectionBuilder.topBearish(signals, settings.topN());

        Map<String, MarketEvent> resultsSoon = new LinkedHashMap<>();
        for (TradeSignal s : listedBullish(sections, bestOverall)) {
            calendar.nextResults(s.symbol(), today, settings.resultsWarningDays())
                    .ifPresent(e -> resultsSoon.put(s.symbol(), e));
        }
        List<MarketEvent> events = calendar.upcoming(today.plusDays(1), EVENT_DAYS);

        MarketContext context = MarketContext.of(index, indexSnapshot);
        String incomplete = IndexLines.incompleteWarning(failed.size(), settings.universe().symbols().size());
        Map<String, LocalDate> resultsDates = new LinkedHashMap<>();
        resultsSoon.forEach((symbol, event) -> resultsDates.put(symbol, event.date()));
        store.saveScan(new ScanReport(TradeStyle.SWING, clock.instant(), lastSession,
                SwingReportFormatter.marketLine(context), bestOverall, worstOverall, resultsDates, events,
                signals.size(), failed, incomplete.isEmpty() ? List.of() : List.of(incomplete.substring(3)), sections));

        int bullishCount = sections.stream().mapToInt(s -> s.bullish().size()).sum();
        int bearishCount = sections.stream().mapToInt(s -> s.bearish().size()).sum();
        boolean sent = dispatcher.send(
                SwingReportFormatter.subject(lastSession, bullishCount, bearishCount) + (incomplete.isEmpty() ? "" : " (incomplete)"),
                SwingReportFormatter.body(context, sections, resultsSoon, events, today, signals.size(), failed));

        EodRunSummary summary = new EodRunSummary(signals.size(), failed.size(), bullishCount, bearishCount, sent, null);
        log.info("EOD scan finished: {}", summary);
        return summary;
    }

    static List<Section> withIndexLines(List<Section> sections, Map<Segment, String> lines) {
        return sections.stream()
                .map(s -> new Section(s.segment(), lines.get(s.segment()), s.scanned(), s.bullish(), s.bearish()))
                .toList();
    }

    private static List<TradeSignal> listedBullish(List<Section> sections, List<TradeSignal> overall) {
        List<TradeSignal> listed = new ArrayList<>(overall);
        sections.forEach(s -> listed.addAll(s.bullish()));
        return listed;
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
