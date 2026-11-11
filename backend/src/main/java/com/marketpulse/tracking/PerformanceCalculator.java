package com.marketpulse.tracking;

import com.marketpulse.signals.Confidence;
import com.marketpulse.signals.TradeStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Turns paper trades into a per-style track record, split by score band and confidence. */
public class PerformanceCalculator {

    private static final List<TradeStyle> ORDER = List.of(TradeStyle.SWING, TradeStyle.POSITIONAL, TradeStyle.BTST);

    public List<StyleStats> summarize(List<TrackedSignal> trades) {
        return ORDER.stream().map(style -> stats(style, trades.stream().filter(t -> t.style() == style).toList())).toList();
    }

    private static StyleStats stats(TradeStyle style, List<TrackedSignal> trades) {
        List<TrackedSignal> closed = trades.stream().filter(t -> t.status().closed()).toList();
        List<Double> returns = closed.stream().map(TrackedSignal::returnPct).toList();
        List<Double> wins = returns.stream().filter(r -> r > 0).toList();
        List<Double> losses = returns.stream().filter(r -> r <= 0).toList();

        List<Bucket> byScore = new ArrayList<>();
        addBucket(byScore, "80-100", closed, t -> t.score() >= 80);
        addBucket(byScore, "65-79", closed, t -> t.score() < 80);
        List<Bucket> byConfidence = new ArrayList<>();
        for (Confidence c : Confidence.values()) {
            addBucket(byConfidence, c.name(), closed, t -> t.confidence() == c);
        }

        return new StyleStats(style, trades.size() - closed.size(), closed.size(), wins.size(),
                share(wins.size(), closed.size()), average(returns), average(wins), average(losses),
                count(closed, TradeStatus.TARGET_HIT), count(closed, TradeStatus.STOPPED), count(closed, TradeStatus.EXPIRED),
                returns.stream().mapToDouble(Double::doubleValue).max().orElse(0),
                returns.stream().mapToDouble(Double::doubleValue).min().orElse(0),
                byScore, byConfidence);
    }

    private static void addBucket(List<Bucket> buckets, String label, List<TrackedSignal> closed,
                                  Predicate<TrackedSignal> filter) {
        List<Double> returns = closed.stream().filter(filter).map(TrackedSignal::returnPct).toList();
        if (!returns.isEmpty()) {
            long wins = returns.stream().filter(r -> r > 0).count();
            buckets.add(new Bucket(label, returns.size(), share((int) wins, returns.size()), average(returns)));
        }
    }

    private static int count(List<TrackedSignal> trades, TradeStatus status) {
        return (int) trades.stream().filter(t -> t.status() == status).count();
    }

    private static double share(int part, int whole) {
        return whole == 0 ? 0 : 100.0 * part / whole;
    }

    private static double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }
}
