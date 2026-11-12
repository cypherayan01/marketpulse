package com.marketpulse.predictions;

import com.marketpulse.signals.TradeStyle;
import com.marketpulse.universe.Segment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Turns predictions into hit rates and edge vs Nifty, per checkpoint and broken down. */
public class ScorecardCalculator {

    private static final List<TradeStyle> ORDER = List.of(TradeStyle.SWING, TradeStyle.POSITIONAL, TradeStyle.BTST);

    public List<StyleScorecard> summarize(List<Prediction> all) {
        return ORDER.stream().map(style -> card(style, all.stream().filter(p -> p.style() == style).toList())).toList();
    }

    static int keySessions(TradeStyle style) {
        return switch (style) {
            case BTST -> 1;
            case SWING -> 5;
            case POSITIONAL -> 20;
        };
    }

    private static StyleScorecard card(TradeStyle style, List<Prediction> predictions) {
        List<Checkpoint> checkpoints = Prediction.CHECKPOINTS.stream()
                .map(k -> checkpoint(k, predictions))
                .toList();
        int key = keySessions(style);

        List<Breakdown> byCall = new ArrayList<>();
        add(byCall, "Buy", predictions, key, p -> p.call() == Call.BUY);
        add(byCall, "Avoid", predictions, key, p -> p.call() == Call.AVOID);

        List<Breakdown> byScore = new ArrayList<>();
        add(byScore, "Buy 80+", predictions, key, p -> p.call() == Call.BUY && p.score() >= 80);
        add(byScore, "Buy 65-79", predictions, key, p -> p.call() == Call.BUY && p.score() < 80);
        add(byScore, "Avoid 0-24", predictions, key, p -> p.call() == Call.AVOID && p.score() < 25);
        add(byScore, "Avoid 25-44", predictions, key, p -> p.call() == Call.AVOID && p.score() >= 25);

        List<Breakdown> bySegment = new ArrayList<>();
        for (Segment s : Segment.values()) {
            add(bySegment, s.title(), predictions, key, p -> p.segment() == s);
        }

        List<Breakdown> byNews = new ArrayList<>();
        add(byNews, "News agreed", predictions, key, p -> newsAgreement(p) > 0);
        add(byNews, "News disagreed", predictions, key, p -> newsAgreement(p) < 0);
        add(byNews, "No news", predictions, key, p -> p.newsSentiment() == 0);

        return new StyleScorecard(style, predictions.size(), checkpoints, key, byCall, byScore, bySegment, byNews,
                nextMorning(predictions));
    }

    /** +1 when the news pointed the same way as the call, -1 when against, 0 without news. */
    private static int newsAgreement(Prediction p) {
        return p.call() == Call.BUY ? Integer.signum(p.newsSentiment()) : -Integer.signum(p.newsSentiment());
    }

    private static Checkpoint checkpoint(int sessions, List<Prediction> predictions) {
        List<Double> edges = predictions.stream().map(p -> p.edgePct(sessions)).filter(Objects::nonNull).toList();
        List<Double> buyReturns = predictions.stream()
                .filter(p -> p.call() == Call.BUY)
                .map(p -> p.returns().get(sessions)).filter(Objects::nonNull).toList();
        return new Checkpoint(sessions, edges.size(), share(edges.stream().filter(e -> e > 0).count(), edges.size()),
                average(edges), average(buyReturns));
    }

    private static void add(List<Breakdown> into, String label, List<Prediction> predictions, int sessions,
                            Predicate<Prediction> filter) {
        List<Double> edges = predictions.stream().filter(filter)
                .map(p -> p.edgePct(sessions)).filter(Objects::nonNull).toList();
        if (!edges.isEmpty()) {
            into.add(new Breakdown(label, edges.size(), share(edges.stream().filter(e -> e > 0).count(), edges.size()),
                    average(edges)));
        }
    }

    private static NextMorning nextMorning(List<Prediction> predictions) {
        List<Prediction> measured = predictions.stream()
                .filter(p -> p.call() == Call.BUY && p.nextOpenPct() != null && p.nextHighPct() != null)
                .toList();
        return new NextMorning(measured.size(),
                share(measured.stream().filter(p -> p.nextOpenPct() > 0).count(), measured.size()),
                average(measured.stream().map(Prediction::nextOpenPct).toList()),
                average(measured.stream().map(Prediction::nextHighPct).toList()));
    }

    private static double share(long part, int whole) {
        return whole == 0 ? 0 : 100.0 * part / whole;
    }

    private static double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }
}
