package com.marketpulse.positional;

import com.marketpulse.alerts.AlertDispatcher;
import com.marketpulse.events.EventCalendar;
import com.marketpulse.events.MarketEvent;
import com.marketpulse.market.MarketDataClient;
import com.marketpulse.reports.IndexLines;
import com.marketpulse.reports.ReportStore;
import com.marketpulse.reports.Section;
import com.marketpulse.reports.SectionBuilder;
import com.marketpulse.reports.ScanReport;
import com.marketpulse.signals.TradeStyle;
import com.marketpulse.universe.Segment;
import com.marketpulse.signals.TradeSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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

    private final Object runLock = new Object();

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

    public PositionalRunSummary run() {
        synchronized (runLock) { // private lock: outside code can never hold it
            return runLocked();
        }
    }

    private PositionalRunSummary runLocked() {
        PositionalSnapshot index;
        try {
            index = calculator.calculate(settings.indexSymbol(), market.dailyCandles(settings.indexSymbol()));
        } catch (RuntimeException e) {
            log.warn("Index data unavailable, skipping positional scan: {}", e.getMessage());
            return new PositionalRunSummary(0, 0, 0, 0, false);
        }

        List<TradeSignal> signals = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        Map<Segment, List<Double>> returns6m = new java.util.EnumMap<>(Segment.class);
        for (String symbol : settings.universe().symbols()) {
            try {
                PositionalSnapshot snapshot = calculator.calculate(symbol, market.dailyCandles(symbol));
                returns6m.computeIfAbsent(settings.universe().segmentOf(symbol), k -> new ArrayList<>()).add(snapshot.return6m());
                signals.add(scorer.score(snapshot, index.return6m()));
            } catch (RuntimeException e) {
                log.warn("Skipping {}: {}", symbol, e.getMessage());
                failed.add(symbol);
            }
        }

        List<Section> grouped = SectionBuilder.build(signals, settings.universe(), settings.topN(), Map.of());
        Map<Segment, String> lines = new java.util.EnumMap<>(
                IndexLines.sixMonths(market, grouped.stream().map(Section::segment).toList()));
        // Yahoo keeps no history for some segment indices: describe the segment by its own stocks instead.
        for (Section s : grouped) {
            lines.computeIfAbsent(s.segment(), seg -> IndexLines.medianOf(seg, returns6m.getOrDefault(seg, List.of())));
        }
        List<Section> sections = grouped.stream()
                .map(s -> new Section(s.segment(), lines.get(s.segment()), s.scanned(), s.bullish(), s.bearish()))
                .toList();
        List<TradeSignal> bestOverall = SectionBuilder.topBullish(signals, settings.topN());
        List<TradeSignal> worstOverall = SectionBuilder.topBearish(signals, settings.topN());

        LocalDate today = LocalDate.now(clock.withZone(IST));
        Map<String, MarketEvent> resultsSoon = new LinkedHashMap<>();
        List<TradeSignal> listed = new ArrayList<>(bestOverall);
        sections.forEach(s -> listed.addAll(s.bullish()));
        for (TradeSignal s : listed) {
            calendar.nextResults(s.symbol(), today, settings.resultsWarningDays())
                    .ifPresent(e -> resultsSoon.put(s.symbol(), e));
        }
        List<MarketEvent> events = calendar.upcoming(today.plusDays(1), EVENT_DAYS);

        Map<String, LocalDate> resultsDates = new LinkedHashMap<>();
        resultsSoon.forEach((symbol, event) -> resultsDates.put(symbol, event.date()));
        store.saveScan(new ScanReport(TradeStyle.POSITIONAL, clock.instant(), index.weekEnding(),
                PositionalReportFormatter.marketLine(index), bestOverall, worstOverall, resultsDates, events,
                signals.size(), failed, List.of(), sections));

        int bullishCount = sections.stream().mapToInt(s -> s.bullish().size()).sum();
        int bearishCount = sections.stream().mapToInt(s -> s.bearish().size()).sum();
        boolean incomplete = !IndexLines.incompleteWarning(failed.size(), settings.universe().symbols().size()).isEmpty();
        boolean sent = dispatcher.send(
                PositionalReportFormatter.subject(index.weekEnding(), bullishCount, bearishCount) + (incomplete ? " (incomplete)" : ""),
                PositionalReportFormatter.body(index, sections, resultsSoon, events, today, signals.size(), failed));

        PositionalRunSummary summary = new PositionalRunSummary(signals.size(), failed.size(), bullishCount,
                bearishCount, sent);
        log.info("Positional scan finished: {}", summary);
        return summary;
    }
}
