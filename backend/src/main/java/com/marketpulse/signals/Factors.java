package com.marketpulse.signals;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Collects the scoring factors behind a signal: each adds (bullish) or subtracts (bearish) points
 * and carries a plain-English explanation.
 */
public final class Factors {

    private record Factor(int points, String text) {
    }

    private final List<Factor> factors = new ArrayList<>();

    public Factors add(int points, String text) {
        if (points != 0) {
            factors.add(new Factor(points, text));
        }
        return this;
    }

    /** {@code +points} with {@code ifTrue} when the condition holds, otherwise {@code -points} with {@code ifFalse}. */
    public Factors either(boolean condition, int points, String ifTrue, String ifFalse) {
        return condition ? add(points, ifTrue) : add(-points, ifFalse);
    }

    public int net() {
        return factors.stream().mapToInt(Factor::points).sum();
    }

    /**
     * 50 is neutral. Net points are scaled by the most the rules can add or subtract, so only a perfect
     * setup scores 100 (or 0) and strong setups stay distinguishable.
     */
    public int score(int maxBullishPoints, int maxBearishPoints) {
        int net = net();
        double max = net >= 0 ? maxBullishPoints : maxBearishPoints;
        return (int) Math.clamp(Math.round(50 + net * 50.0 / max), 0, 100);
    }

    public List<String> bullish() {
        return texts(true);
    }

    public List<String> bearish() {
        return texts(false);
    }

    /** Share of factors agreeing with the overall direction: >= 80% HIGH, >= 60% MEDIUM, else LOW. */
    public Confidence confidence(int score, boolean reliableHistory) {
        if (!reliableHistory || score == 50 || factors.isEmpty()) {
            return Confidence.LOW;
        }
        int direction = Integer.signum(score - 50);
        long agreeing = factors.stream().filter(f -> Integer.signum(f.points()) == direction).count();
        double agreement = (double) agreeing / factors.size();
        if (agreement >= 0.8) return Confidence.HIGH;
        if (agreement >= 0.6) return Confidence.MEDIUM;
        return Confidence.LOW;
    }

    private List<String> texts(boolean positive) {
        return factors.stream()
                .filter(f -> positive ? f.points() > 0 : f.points() < 0)
                .sorted(Comparator.comparingInt((Factor f) -> Math.abs(f.points())).reversed())
                .map(Factor::text)
                .toList();
    }
}
