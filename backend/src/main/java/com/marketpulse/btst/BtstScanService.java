package com.marketpulse.btst;

import com.marketpulse.alerts.AlertDispatcher;
import com.marketpulse.events.EventCalendar;
import com.marketpulse.events.EventType;
import com.marketpulse.events.MarketEvent;
import com.marketpulse.market.Candle;
import com.marketpulse.market.IntradayDataClient;
import com.marketpulse.market.MarketDataClient;
import com.marketpulse.reports.IndexLines;
import com.marketpulse.reports.ReportStore;
import com.marketpulse.reports.Section;
import com.marketpulse.reports.SectionBuilder;
import com.marketpulse.reports.ScanReport;
import com.marketpulse.signals.TradeStyle;
import com.marketpulse.universe.Segment;
import com.marketpulse.signals.Classification;
import com.marketpulse.signals.TradeSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/** ~3 PM IST: find stocks closing strong enough to hold overnight. */
public class BtstScanService {

    private static final Logger log = LoggerFactory.getLogger(BtstScanService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalTime OPEN = LocalTime.of(9, 15);
    private static final LocalTime CLOSE = LocalTime.of(15, 30);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final MarketDataClient dailyData;
    private final IntradayDataClient intradayData;
    private final BtstCalculator calculator;
    private final BtstScorer scorer;
    private final AlertDispatcher dispatcher;
    private final EventCalendar calendar;
    private final Clock clock;
    private final BtstSettings settings;
    private final ReportStore store;
    private final Supplier<PriceRange> priceRange;

    private final Object runLock = new Object();

    public BtstScanService(MarketDataClient dailyData, IntradayDataClient intradayData, BtstCalculator calculator,
                           BtstScorer scorer, AlertDispatcher dispatcher, EventCalendar calendar, Clock clock,
                           BtstSettings settings, ReportStore store) {
        this(dailyData, intradayData, calculator, scorer, dispatcher, calendar, clock, settings, store, () -> PriceRange.ANY);
    }

    /** @param priceRange the dashboard's share-price window, read at the start of every scan */
    public BtstScanService(MarketDataClient dailyData, IntradayDataClient intradayData, BtstCalculator calculator,
                           BtstScorer scorer, AlertDispatcher dispatcher, EventCalendar calendar, Clock clock,
                           BtstSettings settings, ReportStore store,
                           Supplier<PriceRange> priceRange) {
        this.dailyData = dailyData;
        this.intradayData = intradayData;
        this.calculator = calculator;
        this.scorer = scorer;
        this.dispatcher = dispatcher;
        this.calendar = calendar;
        this.clock = clock;
        this.settings = settings;
        this.store = store;
        this.priceRange = priceRange;
    }

    /** @param force run even without a session today (testing) */
    public BtstRunSummary run(boolean force) {
        synchronized (runLock) { // private lock: outside code can never hold it
            return runLocked(force);
        }
    }

    private BtstRunSummary runLocked(boolean force) {
        LocalDateTime now = LocalDateTime.now(clock.withZone(IST));
        LocalDate today = now.toLocalDate();
        List<Candle> index;
        try {
            index = dailyData.dailyCandles(settings.indexSymbol());
        } catch (RuntimeException e) {
            return BtstRunSummary.skipped("Index data unavailable: " + e.getMessage());
        }
        if (!force && !index.getLast().date().equals(today)) {
            return BtstRunSummary.skipped("Market closed today (last session %s)".formatted(DAY.format(index.getLast().date())));
        }
        double niftyChange = (index.getLast().close() / index.get(index.size() - 2).close() - 1) * 100;
        double sessionFraction = sessionFraction(now.toLocalTime());

        // results or macro events before tomorrow's session make an overnight hold a gamble
        int daysToNextSession = today.getDayOfWeek() == DayOfWeek.FRIDAY ? 3 : 1;
        List<MarketEvent> overnight = calendar.upcoming(today, daysToNextSession).stream()
                .filter(e -> e.type() == EventType.MACRO)
                .toList();

        // Pass 1: every stock on today's daily candle alone (no intraday request yet).
        PriceRange range = priceRange.get();
        int outsideRange = 0;
        List<Candidate> prescored = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        List<String> resultsDue = new ArrayList<>();
        for (String symbol : settings.universe().symbols()) {
            try {
                List<Candle> daily = dailyData.dailyCandles(symbol);
                if (!range.contains(daily.getLast().close())) {
                    outsideRange++; // not affordable / not wanted: don't spend an intraday request on it
                    continue;
                }
                LocalDate results = calendar.nextResults(symbol, today, daysToNextSession)
                        .map(MarketEvent::date).orElse(null);
                if (results != null) {
                    resultsDue.add(symbol);
                }
                TradeSignal pre = scorer.score(calculator.calculate(symbol, daily, List.of(), sessionFraction),
                        niftyChange, results);
                prescored.add(new Candidate(symbol, daily, results, pre));
            } catch (RuntimeException e) {
                log.warn("Skipping {}: {}", symbol, e.getMessage());
                failed.add(symbol);
            }
        }

        // Pass 2: only the most promising per segment get 15-minute bars and a full score.
        List<TradeSignal> signals = new ArrayList<>();
        for (Segment segment : Segment.values()) {
            List<Candidate> shortlist = prescored.stream()
                    .filter(c -> settings.universe().segmentOf(c.symbol()) == segment)
                    .filter(c -> !c.prescore().classification().bearish() && c.prescore().score() >= 50)
                    .sorted(Comparator.comparingInt((Candidate c) -> c.prescore().score()).reversed())
                    .limit(settings.intradayPerSegment())
                    .toList();
            for (Candidate c : shortlist) {
                try {
                    BtstSnapshot full = calculator.calculate(c.symbol(), c.daily(),
                            intradayData.intradayCandles(c.symbol()), sessionFraction);
                    signals.add(scorer.score(full, niftyChange, c.resultsDate()));
                } catch (RuntimeException e) {
                    log.warn("No intraday bars for {}: {}", c.symbol(), e.getMessage());
                }
            }
        }

        // Overnight risk is real: only STRONG setups (score 80+) are BTST candidates.
        List<TradeSignal> strong = signals.stream()
                .filter(s -> s.classification() == Classification.STRONG_BULLISH)
                .toList();
        List<Section> grouped = SectionBuilder.build(strong, settings.universe(), settings.topN(), Map.of());
        Map<Segment, String> lines = IndexLines.today(dailyData, grouped.stream().map(Section::segment).toList());
        List<Section> sections = grouped.stream()
                .map(s -> new Section(s.segment(), lines.get(s.segment()), s.scanned(), s.bullish(), List.of()))
                .filter(s -> !s.bullish().isEmpty())
                .toList();
        List<TradeSignal> candidates = SectionBuilder.topBullish(strong, settings.topN());
        int candidateCount = sections.stream().mapToInt(s -> s.bullish().size()).sum();

        List<String> notes = new ArrayList<>();
        String incomplete = IndexLines.incompleteWarning(failed.size(), settings.universe().symbols().size());
        if (!incomplete.isEmpty()) {
            notes.add(incomplete.substring(3)); // the dashboard adds its own warning icon
        }
        String rangeNote = range.isAny() ? "" : "Price range %s: %d stock%s outside it skipped"
                .formatted(range.label(), outsideRange, outsideRange == 1 ? "" : "s");
        if (!rangeNote.isEmpty()) {
            notes.add(rangeNote);
        }
        overnight.forEach(e -> notes.add("Overnight event risk: " + e.title()));
        if (!resultsDue.isEmpty()) {
            notes.add("Excluded - Results due: " + String.join(", ", resultsDue));
        }
        store.saveScan(new ScanReport(TradeStyle.BTST, clock.instant(), today,
                String.format(Locale.ENGLISH, "Nifty %+.2f%% today", niftyChange), candidates, List.of(), Map.of(),
                overnight, prescored.size(), failed, notes, sections));

        boolean sent = dispatcher.send(BtstReportFormatter.subject(today, candidateCount) + (incomplete.isEmpty() ? "" : " (incomplete)"),
                BtstReportFormatter.body(niftyChange, sections, overnight, resultsDue, prescored.size(), failed)
                        + (rangeNote.isEmpty() ? "" : "\n" + rangeNote + "\n"));
        BtstRunSummary summary = new BtstRunSummary(prescored.size(), failed.size(), candidateCount, sent, null);
        log.info("BTST scan finished: {} ({} intraday requests)", summary, signals.size());
        return summary;
    }

    private record Candidate(String symbol, List<Candle> daily, LocalDate resultsDate, TradeSignal prescore) {
    }

    /** Share of the 09:15-15:30 session elapsed; 1.0 outside market hours. */
    static double sessionFraction(LocalTime now) {
        if (!now.isAfter(OPEN) || !now.isBefore(CLOSE)) {
            return 1.0;
        }
        return (double) Duration.between(OPEN, now).toMinutes() / Duration.between(OPEN, CLOSE).toMinutes();
    }
}
