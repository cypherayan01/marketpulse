package com.marketpulse.analysis;

import com.marketpulse.events.EventCalendar;
import com.marketpulse.events.MarketEvent;
import com.marketpulse.holdings.Holding;
import com.marketpulse.holdings.HoldingCheck;
import com.marketpulse.holdings.HoldingEvaluator;
import com.marketpulse.holdings.MarketReturns;
import com.marketpulse.market.Candle;
import com.marketpulse.market.MarketDataClient;
import com.marketpulse.positional.PositionalCalculator;
import com.marketpulse.positional.PositionalScorer;
import com.marketpulse.reports.NewsEntry;
import com.marketpulse.reports.ReportStore;
import com.marketpulse.signals.TradeSignal;
import com.marketpulse.signals.TradeStyle;
import com.marketpulse.swing.IndicatorCalculator;
import com.marketpulse.swing.SwingScorer;
import com.marketpulse.universe.Stock;
import com.marketpulse.universe.Universe;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * On-demand view of one stock: the same swing (daily) and positional (weekly) rules as the scans, combined into
 * a verdict for a new buyer, plus keep / exit if you already own it.
 */
public class StockAnalyzer {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int RESULTS_LOOKAHEAD_DAYS = 30;
    private static final int RESULTS_WARNING_DAYS = 15;
    private static final int MAX_NEWS = 5;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);

    private final MarketDataClient market;
    private final Universe universe;
    private final EventCalendar calendar;
    private final ReportStore store;
    private final Clock clock;
    private final String indexSymbol;
    private final IndicatorCalculator daily = new IndicatorCalculator();
    private final PositionalCalculator weekly = new PositionalCalculator();
    private final SwingScorer swingScorer = new SwingScorer();
    private final PositionalScorer positionalScorer = new PositionalScorer();
    private final HoldingEvaluator holdingEvaluator = new HoldingEvaluator();

    public StockAnalyzer(MarketDataClient market, Universe universe, EventCalendar calendar, ReportStore store,
                         Clock clock, String indexSymbol) {
        this.market = market;
        this.universe = universe;
        this.calendar = calendar;
        this.store = store;
        this.clock = clock;
        this.indexSymbol = indexSymbol;
    }

    public StockAnalysis analyze(String rawSymbol, Optional<OwnedPosition> owned) {
        String symbol = rawSymbol.trim().toUpperCase(Locale.ROOT);
        List<Candle> candles;
        try {
            candles = market.dailyCandles(symbol);
        } catch (RuntimeException e) {
            throw new UnknownStockException(symbol);
        }
        if (candles.size() < 2) {
            throw new UnknownStockException(symbol);
        }
        List<Candle> index = market.dailyCandles(indexSymbol);
        MarketReturns nifty = new MarketReturns(daily.calculate(indexSymbol, index).return3m(),
                weekly.calculate(indexSymbol, index).return6m());

        TradeSignal swing = null;
        try {
            swing = swingScorer.score(daily.calculate(symbol, candles), nifty.return3m());
        } catch (IllegalArgumentException e) {
            // too new even for the daily view - handled below as "no view"
        }
        TradeSignal positional = null;
        String positionalUnavailable = null;
        try {
            positional = positionalScorer.score(weekly.calculate(symbol, candles), nifty.return6m());
        } catch (IllegalArgumentException e) {
            positionalUnavailable = e.getMessage() + " - the weekly (positional) view needs about a year of history";
        }

        LocalDate today = LocalDate.now(clock.withZone(IST));
        LocalDate resultsDate = calendar.nextResults(symbol, today, RESULTS_LOOKAHEAD_DAYS).map(MarketEvent::date).orElse(null);
        AnalysisVerdict verdict = verdict(swing, positional);
        List<String> reasons = reasons(verdict, swing, positional, resultsDate, today);

        boolean noWeeklyView = positional == null;
        HoldingCheck holding = owned.map(o -> checkHolding(symbol, o, candles, nifty, resultsDate, noWeeklyView))
                .orElse(null);

        Optional<Stock> stock = universe.find(symbol);
        Candle last = candles.getLast();
        double dayChange = (last.close() / candles.get(candles.size() - 2).close() - 1) * 100;
        return new StockAnalysis(symbol, stock.map(Stock::name).orElse(symbol), stock.map(Stock::industry).orElse(""),
                stock.map(Stock::segment).orElse(null), last.close(), last.date(), dayChange, verdict,
                headline(verdict), reasons, swing, positional, positionalUnavailable, holding, resultsDate,
                news(symbol, stock), clock.instant());
    }

    static AnalysisVerdict verdict(TradeSignal swing, TradeSignal positional) {
        boolean swingUp = swing != null && swing.classification().bullish();
        boolean swingDown = swing != null && swing.classification().bearish();
        boolean longUp = positional != null && positional.classification().bullish();
        boolean longDown = positional != null && positional.classification().bearish();
        if (swingUp && longUp) return AnalysisVerdict.BUY;
        if (swingUp && !longDown) return AnalysisVerdict.BUY_SWING;
        if (longUp && !swingDown) return AnalysisVerdict.ACCUMULATE;
        if (swingDown && (longDown || positional == null)) return AnalysisVerdict.AVOID;
        return AnalysisVerdict.WAIT;
    }

    static String headline(AnalysisVerdict verdict) {
        return switch (verdict) {
            case BUY -> "Buy - short-term and long-term trends agree";
            case BUY_SWING -> "Buy for a swing trade only - the long-term trend isn't confirmed";
            case ACCUMULATE -> "Good long-term trend, weak short-term - wait for a better entry or buy in parts";
            case WAIT -> "Wait - the signals are mixed";
            case AVOID -> "Avoid - the trend is down (in the cash market \"sell\" means don't buy, or exit if you hold)";
        };
    }

    private static List<String> reasons(AnalysisVerdict verdict, TradeSignal swing, TradeSignal positional,
                                        LocalDate resultsDate, LocalDate today) {
        List<String> reasons = new ArrayList<>();
        boolean positive = verdict == AnalysisVerdict.BUY || verdict == AnalysisVerdict.BUY_SWING
                || verdict == AnalysisVerdict.ACCUMULATE;
        if (swing != null) {
            reasons.add("Swing (days-weeks): %s, score %d".formatted(swing.classification().label().toLowerCase(Locale.ROOT), swing.score()));
        }
        if (positional != null) {
            reasons.add("Positional (months): %s, score %d".formatted(positional.classification().label().toLowerCase(Locale.ROOT), positional.score()));
        }
        TradeSignal lead = verdict == AnalysisVerdict.ACCUMULATE ? positional : swing != null ? swing : positional;
        if (lead != null) {
            (positive ? lead.bullishFactors() : lead.bearishFactors()).stream().limit(3).forEach(reasons::add);
        }
        if (resultsDate != null && !resultsDate.isAfter(today.plusDays(RESULTS_WARNING_DAYS))) {
            reasons.add("Results on %s - a results surprise can move the stock sharply; consider waiting for the numbers"
                    .formatted(DAY.format(resultsDate)));
        }
        return reasons;
    }

    private HoldingCheck checkHolding(String symbol, OwnedPosition o, List<Candle> candles, MarketReturns nifty,
                                      LocalDate resultsDate, boolean noWeeklyView) {
        // Without a year of history the weekly view can't be built, so judge the holding on the daily chart.
        TradeStyle horizon = o.horizon() == TradeStyle.POSITIONAL && noWeeklyView ? TradeStyle.SWING : o.horizon();
        Holding holding = new Holding(null, symbol, o.quantity(), o.buyPrice(), o.buyDate(), horizon, o.stopLoss(), null,
                clock.instant());
        return holdingEvaluator.evaluate(holding, candles, nifty, resultsDate);
    }

    /** Scored headlines that name the stock (by symbol, or by its company name). */
    private List<NewsEntry> news(String symbol, Optional<Stock> stock) {
        String name = stock.map(s -> s.name().replaceAll("(?i)\\s+(ltd\\.?|limited)$", "").trim().toLowerCase(Locale.ROOT))
                .filter(n -> n.length() >= 4)
                .orElse(null);
        return store.recentNews().stream()
                .filter(n -> n.stocks().contains(symbol)
                        || (name != null && n.title().toLowerCase(Locale.ROOT).contains(name)))
                .limit(MAX_NEWS)
                .toList();
    }
}
