package com.marketpulse.predictions;

import com.marketpulse.btst.BtstCalculator;
import com.marketpulse.btst.BtstScorer;
import com.marketpulse.market.Candle;
import com.marketpulse.market.MarketDataClient;
import com.marketpulse.positional.PositionalCalculator;
import com.marketpulse.positional.PositionalScorer;
import com.marketpulse.positional.PositionalSnapshot;
import com.marketpulse.signals.Classification;
import com.marketpulse.signals.TradeSignal;
import com.marketpulse.signals.TradeStyle;
import com.marketpulse.swing.IndicatorCalculator;
import com.marketpulse.swing.IndicatorSnapshot;
import com.marketpulse.swing.SwingScorer;
import com.marketpulse.universe.Segment;
import com.marketpulse.universe.Universe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Replays the past year: on each past trading day, scores every stock with only the data known that day (the
 * calculators are causal and tested for it), picks the same top calls per section as the live reports, then
 * checks them against what actually happened. An estimate: it uses today's index lists (survivorship bias),
 * no news, and BTST without the last-hour intraday data.
 */
public class ReplayService {

    private static final Logger log = LoggerFactory.getLogger(ReplayService.class);

    private record Scored(String symbol, int score, Classification classification, double close) {
    }

    private final MarketDataClient market;
    private final Universe universe;
    private final String indexSymbol;
    private final PredictionRepository repository;
    private final OutcomeCalculator outcomes;
    private final Clock clock;
    private final ReplaySettings settings;
    private final IndicatorCalculator daily = new IndicatorCalculator();
    private final PositionalCalculator weekly = new PositionalCalculator();
    private final SwingScorer swingScorer = new SwingScorer();
    private final PositionalScorer positionalScorer = new PositionalScorer();
    private final BtstCalculator btstCalculator = new BtstCalculator();
    private final BtstScorer btstScorer = new BtstScorer();

    private final Object runLock = new Object();

    public ReplayService(MarketDataClient market, Universe universe, String indexSymbol, PredictionRepository repository,
                         OutcomeCalculator outcomes, Clock clock, ReplaySettings settings) {
        this.market = market;
        this.universe = universe;
        this.indexSymbol = indexSymbol;
        this.repository = repository;
        this.outcomes = outcomes;
        this.clock = clock;
        this.settings = settings;
    }

    public ReplaySummary run() {
        synchronized (runLock) { // private lock: outside code can never hold it
            return runLocked();
        }
    }

    private ReplaySummary runLocked() {
        long start = System.nanoTime();
        List<Candle> nifty = market.dailyCandles(indexSymbol);
        int first = Math.max(IndicatorCalculatorMinimum.BARS, nifty.size() - settings.sessions());
        List<LocalDate> days = nifty.subList(first, nifty.size()).stream().map(Candle::date).toList();

        Map<LocalDate, Double> niftyClose = new HashMap<>();
        Map<LocalDate, Double> niftyDayChange = new HashMap<>();
        for (int i = first; i < nifty.size(); i++) {
            niftyClose.put(nifty.get(i).date(), nifty.get(i).close());
            niftyDayChange.put(nifty.get(i).date(), (nifty.get(i).close() / nifty.get(i - 1).close() - 1) * 100);
        }
        Map<LocalDate, Double> niftyReturn3m = new HashMap<>();
        daily.snapshotsFrom(indexSymbol, nifty, first).forEach(s -> niftyReturn3m.put(s.date(), s.return3m()));
        Set<LocalDate> weekEnds = weekEnds(days);
        Map<LocalDate, Double> niftyReturn6m = new HashMap<>();
        weekly.weeklySnapshots(indexSymbol, nifty, 0).forEach(s -> niftyReturn6m.put(s.weekEnding(), s.return6m()));

        Map<LocalDate, List<Scored>> swing = new TreeMap<>();
        Map<LocalDate, List<Scored>> btst = new TreeMap<>();
        Map<LocalDate, List<Scored>> positional = new TreeMap<>();
        int stocks = 0;
        int failed = 0;
        for (String symbol : universe.symbols()) {
            try {
                List<Candle> candles = market.dailyCandles(symbol);
                int from = firstIndexOnOrAfter(candles, days.getFirst());
                for (IndicatorSnapshot snap : daily.snapshotsFrom(symbol, candles, Math.max(from, 1))) {
                    Double rs = niftyReturn3m.get(snap.date());
                    if (rs == null) {
                        continue; // not a replayed day
                    }
                    TradeSignal s = swingScorer.score(snap, rs);
                    swing.computeIfAbsent(snap.date(), d -> new ArrayList<>())
                            .add(new Scored(symbol, s.score(), s.classification(), s.close()));
                    int i = snap.barCount() - 1;
                    TradeSignal b = btstScorer.score(btstCalculator.fromSnapshot(snap, candles.get(i), candles.get(i - 1),
                            List.of(), 1.0), niftyDayChange.get(snap.date()), null);
                    if (b.classification() == Classification.STRONG_BULLISH) {
                        btst.computeIfAbsent(snap.date(), d -> new ArrayList<>())
                                .add(new Scored(symbol, b.score(), b.classification(), b.close()));
                    }
                }
                replayWeekly(symbol, candles, weekEnds, niftyReturn6m, positional);
                stocks++;
            } catch (RuntimeException e) {
                failed++;
            }
        }

        Instant now = clock.instant();
        List<Prediction> predictions = new ArrayList<>();
        swing.forEach((day, scored) -> predictions.addAll(pick(TradeStyle.SWING, day, scored, settings.perSection(),
                settings.perSection(), niftyClose.get(day), now)));
        btst.forEach((day, scored) -> predictions.addAll(pick(TradeStyle.BTST, day, scored, settings.btstPerSection(),
                0, niftyClose.get(day), now)));
        positional.forEach((day, scored) -> predictions.addAll(pick(TradeStyle.POSITIONAL, day, scored,
                settings.perSection(), settings.perSection(), niftyClose.get(day), now)));

        Map<String, List<Candle>> bySymbol = new HashMap<>();
        List<Prediction> checked = new ArrayList<>(predictions.size());
        for (Prediction p : predictions) {
            List<Candle> candles = bySymbol.computeIfAbsent(p.symbol(), market::dailyCandles);
            checked.add(outcomes.fill(p, candles, nifty));
        }
        repository.replaceReplay(checked);

        ReplaySummary summary = new ReplaySummary(days.size(), stocks, failed, checked.size(),
                (System.nanoTime() - start) / 1_000_000_000);
        log.info("Replay finished: {}", summary);
        return summary;
    }

    private void replayWeekly(String symbol, List<Candle> candles, Set<LocalDate> weekEnds,
                              Map<LocalDate, Double> niftyReturn6m, Map<LocalDate, List<Scored>> into) {
        List<PositionalSnapshot> snapshots;
        try {
            snapshots = weekly.weeklySnapshots(symbol, candles, 0);
        } catch (IllegalArgumentException e) {
            return; // under a year of history: no weekly view, swing/BTST still replayed
        }
        for (PositionalSnapshot w : snapshots) {
            Double rs = niftyReturn6m.get(w.weekEnding());
            if (rs != null && weekEnds.contains(w.weekEnding())) {
                TradeSignal s = positionalScorer.score(w, rs);
                into.computeIfAbsent(w.weekEnding(), d -> new ArrayList<>())
                        .add(new Scored(symbol, s.score(), s.classification(), s.close()));
            }
        }
    }

    /** The same selection as the live reports: per segment the best buys and the worst stocks to avoid. */
    private List<Prediction> pick(TradeStyle style, LocalDate day, List<Scored> scored, int buys, int avoids,
                                  Double nifty, Instant now) {
        List<Prediction> picked = new ArrayList<>();
        for (Segment segment : Segment.values()) {
            List<Scored> inSegment = scored.stream().filter(s -> universe.segmentOf(s.symbol()) == segment).toList();
            inSegment.stream().filter(s -> s.classification().bullish())
                    .sorted(Comparator.comparingInt(Scored::score).reversed()).limit(buys)
                    .forEach(s -> picked.add(prediction(style, day, s, segment, Call.BUY, nifty, now)));
            inSegment.stream().filter(s -> s.classification().bearish())
                    .sorted(Comparator.comparingInt(Scored::score)).limit(avoids)
                    .forEach(s -> picked.add(prediction(style, day, s, segment, Call.AVOID, nifty, now)));
        }
        return picked;
    }

    private static Prediction prediction(TradeStyle style, LocalDate day, Scored s, Segment segment, Call call,
                                         Double nifty, Instant now) {
        return Prediction.of(PredictionSource.REPLAY, style, s.symbol(), segment, day, s.score(), s.classification(),
                call, s.close(), nifty == null ? 0 : nifty, 0, now);
    }

    /** Last trading day of each ISO week among the replayed days. */
    private static Set<LocalDate> weekEnds(List<LocalDate> days) {
        Set<LocalDate> ends = new HashSet<>();
        for (int i = 0; i < days.size(); i++) {
            boolean lastOfWeek = i == days.size() - 1 || week(days.get(i + 1)) != week(days.get(i));
            if (lastOfWeek) {
                ends.add(days.get(i));
            }
        }
        return ends;
    }

    private static long week(LocalDate d) {
        return d.get(IsoFields.WEEK_BASED_YEAR) * 100L + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    private static int firstIndexOnOrAfter(List<Candle> candles, LocalDate day) {
        for (int i = 0; i < candles.size(); i++) {
            if (!candles.get(i).date().isBefore(day)) {
                return i;
            }
        }
        return candles.size();
    }

    /** Enough daily history before the first replayed day for the 200-day average. */
    private static final class IndicatorCalculatorMinimum {
        static final int BARS = 200;
    }
}
