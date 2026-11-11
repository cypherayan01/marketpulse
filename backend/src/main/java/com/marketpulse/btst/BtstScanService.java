package com.marketpulse.btst;

import com.marketpulse.alerts.AlertDispatcher;
import com.marketpulse.events.EventCalendar;
import com.marketpulse.events.EventType;
import com.marketpulse.events.MarketEvent;
import com.marketpulse.market.Candle;
import com.marketpulse.market.IntradayDataClient;
import com.marketpulse.market.MarketDataClient;
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

    public BtstScanService(MarketDataClient dailyData, IntradayDataClient intradayData, BtstCalculator calculator,
                           BtstScorer scorer, AlertDispatcher dispatcher, EventCalendar calendar, Clock clock,
                           BtstSettings settings) {
        this.dailyData = dailyData;
        this.intradayData = intradayData;
        this.calculator = calculator;
        this.scorer = scorer;
        this.dispatcher = dispatcher;
        this.calendar = calendar;
        this.clock = clock;
        this.settings = settings;
    }

    /** @param force run even without a session today (testing) */
    public synchronized BtstRunSummary run(boolean force) {
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

        List<TradeSignal> signals = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        List<String> resultsDue = new ArrayList<>();
        for (String symbol : settings.universe()) {
            try {
                BtstSnapshot snapshot = calculator.calculate(symbol, dailyData.dailyCandles(symbol),
                        intradayData.intradayCandles(symbol), sessionFraction);
                LocalDate results = calendar.nextResults(symbol, today, daysToNextSession)
                        .map(MarketEvent::date).orElse(null);
                if (results != null) {
                    resultsDue.add(symbol);
                }
                signals.add(scorer.score(snapshot, niftyChange, results));
            } catch (RuntimeException e) {
                log.warn("Skipping {}: {}", symbol, e.getMessage());
                failed.add(symbol);
            }
        }

        List<TradeSignal> candidates = signals.stream()
                .filter(s -> s.classification().bullish())
                .sorted(Comparator.comparingInt(TradeSignal::score).reversed())
                .limit(settings.topN())
                .toList();

        boolean sent = dispatcher.send(BtstReportFormatter.subject(today, candidates.size()),
                BtstReportFormatter.body(niftyChange, candidates, overnight, resultsDue, signals.size(), failed));
        BtstRunSummary summary = new BtstRunSummary(signals.size(), failed.size(), candidates.size(), sent, null);
        log.info("BTST scan finished: {}", summary);
        return summary;
    }

    /** Share of the 09:15-15:30 session elapsed; 1.0 outside market hours. */
    static double sessionFraction(LocalTime now) {
        if (!now.isAfter(OPEN) || !now.isBefore(CLOSE)) {
            return 1.0;
        }
        return (double) Duration.between(OPEN, now).toMinutes() / Duration.between(OPEN, CLOSE).toMinutes();
    }
}
