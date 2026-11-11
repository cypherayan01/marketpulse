package com.marketpulse.swing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Rule-based swing-trade scoring on the daily chart. Starts at 50 (neutral); each signal adds or subtracts points.
 *
 * <pre>
 * Trend      close vs 200 EMA ±10 · 20 EMA vs 50 EMA ±8 · close vs 20 EMA ±5
 * Momentum   MACD vs signal ±6 · fresh MACD crossover ±4 · RSI zone -6..+6
 * Price      20-day breakout/breakdown ±10, +-5 more on >= 1.5x volume · within 5% of 52-week high +4
 * Strength   3-month return vs Nifty beyond ±5 points: ±8
 * </pre>
 * Net points are scaled so only a perfect bullish setup scores 100 and only a perfect breakdown scores 0;
 * this keeps strong setups distinguishable instead of all clamping at the top.
 */
public class SwingScorer {

    private static final double VOLUME_CONFIRMATION = 1.5;
    private static final double RELATIVE_STRENGTH_EDGE = 0.05;
    private static final int RELIABLE_HISTORY = 200;
    /** Most points the rules can add: trend 23 + MACD 10 + RSI 6 + breakout 15 + 52-week high 4 + strength 8. */
    static final int MAX_BULLISH_POINTS = 66;
    /** Most points the rules can subtract: trend 23 + MACD 10 + RSI 6 + breakdown 15 + strength 8. */
    static final int MAX_BEARISH_POINTS = 62;

    public SwingSignal score(IndicatorSnapshot s, double niftyReturn3m) {
        List<Signal> signals = new ArrayList<>();

        // Trend
        signals.add(s.close() > s.ema200()
                ? new Signal(10, "Above 200-day EMA (long-term uptrend)")
                : new Signal(-10, "Below 200-day EMA (long-term downtrend)"));
        signals.add(s.ema20() > s.ema50()
                ? new Signal(8, "20-day EMA above 50-day (medium-term uptrend)")
                : new Signal(-8, "20-day EMA below 50-day (medium-term downtrend)"));
        signals.add(s.close() > s.ema20()
                ? new Signal(5, "Price above 20-day EMA")
                : new Signal(-5, "Price below 20-day EMA"));

        // Momentum
        signals.add(s.macd() > s.macdSignal()
                ? new Signal(6, "MACD above signal line")
                : new Signal(-6, "MACD below signal line"));
        if (s.macdCross() == MacdCross.BULLISH) {
            signals.add(new Signal(4, "Fresh MACD bullish crossover"));
        } else if (s.macdCross() == MacdCross.BEARISH) {
            signals.add(new Signal(-4, "Fresh MACD bearish crossover"));
        }
        signals.add(rsiSignal(s.rsi14()));

        // Price action
        double volumeRatio = s.avgVolume20() > 0 ? s.volume() / s.avgVolume20() : 0;
        boolean volumeConfirmed = volumeRatio >= VOLUME_CONFIRMATION;
        if (s.close() > s.prior20High()) {
            signals.add(new Signal(10, "Broke above 20-day high (%s)".formatted(rupees(s.prior20High()))));
            if (volumeConfirmed) {
                signals.add(new Signal(5, String.format(Locale.ROOT, "Breakout on %.1fx average volume", volumeRatio)));
            }
        } else if (s.close() < s.prior20Low()) {
            signals.add(new Signal(-10, "Broke below 20-day low (%s)".formatted(rupees(s.prior20Low()))));
            if (volumeConfirmed) {
                signals.add(new Signal(-5, String.format(Locale.ROOT, "Breakdown on %.1fx average volume", volumeRatio)));
            }
        }
        if (s.close() >= 0.95 * s.high52w()) {
            signals.add(new Signal(4, "Within 5% of 52-week high"));
        }

        // Relative strength
        double edge = s.return3m() - niftyReturn3m;
        if (edge > RELATIVE_STRENGTH_EDGE) {
            signals.add(new Signal(8, String.format(Locale.ROOT, "Outperformed Nifty by %.1f%% over 3 months", edge * 100)));
        } else if (edge < -RELATIVE_STRENGTH_EDGE) {
            signals.add(new Signal(-8, String.format(Locale.ROOT, "Underperformed Nifty by %.1f%% over 3 months", -edge * 100)));
        }

        int score = scale(signals.stream().mapToInt(Signal::points).sum());
        Classification classification = Classification.of(score);
        List<String> bullishFactors = texts(signals, true);
        List<String> bearishFactors = texts(signals, false);

        TradePlan plan = null;
        String invalidation = null;
        if (classification.bullish()) {
            plan = plan(s);
            invalidation = s.close() > s.prior20High()
                    ? "Daily close back below %s (failed breakout)".formatted(rupees(s.prior20High()))
                    : "Daily close below %s (stop loss)".formatted(rupees(plan.stopLoss()));
        }

        return new SwingSignal(s.symbol(), score, classification, confidence(signals, score, s.barCount()),
                bullishFactors, bearishFactors, plan, invalidation, s);
    }

    private static int scale(int netPoints) {
        double max = netPoints >= 0 ? MAX_BULLISH_POINTS : MAX_BEARISH_POINTS;
        return (int) Math.clamp(Math.round(50 + netPoints * 50.0 / max), 0, 100);
    }

    private static List<String> texts(List<Signal> signals, boolean positive) {
        return signals.stream()
                .filter(sig -> positive ? sig.points() > 0 : sig.points() < 0)
                .sorted(Comparator.comparingInt((Signal sig) -> Math.abs(sig.points())).reversed())
                .map(Signal::text)
                .toList();
    }

    private static Signal rsiSignal(double rsi) {
        String value = String.format(Locale.ROOT, "%.0f", rsi);
        if (rsi >= 75) return new Signal(-4, "RSI " + value + " overbought - pullback risk, avoid chasing");
        if (rsi >= 65) return new Signal(2, "RSI " + value + " strong but getting stretched");
        if (rsi >= 50) return new Signal(6, "RSI " + value + " healthy momentum");
        if (rsi >= 40) return new Signal(-3, "RSI " + value + " weak momentum");
        if (rsi >= 30) return new Signal(-6, "RSI " + value + " bearish momentum");
        return new Signal(-2, "RSI " + value + " oversold - wait for a reversal before buying");
    }

    /** Stop: the tighter of 2 ATR and the 10-day swing low, but never closer than 1 ATR. Target: 2x the risk. */
    private static TradePlan plan(IndicatorSnapshot s) {
        double entry = s.close();
        double stop = Math.min(Math.max(entry - 2 * s.atr14(), s.swingLow10()), entry - s.atr14());
        double risk = entry - stop;
        return new TradePlan(entry, stop, entry + 2 * risk, risk / entry * 100);
    }

    private static Confidence confidence(List<Signal> signals, int score, int barCount) {
        if (barCount < RELIABLE_HISTORY || score == 50) {
            return Confidence.LOW;
        }
        int direction = Integer.signum(score - 50);
        long voting = signals.stream().filter(s -> s.points() != 0).count();
        long agreeing = signals.stream().filter(s -> Integer.signum(s.points()) == direction).count();
        double agreement = voting == 0 ? 0 : (double) agreeing / voting;
        if (agreement >= 0.8) return Confidence.HIGH;
        if (agreement >= 0.6) return Confidence.MEDIUM;
        return Confidence.LOW;
    }

    static String rupees(double price) {
        return String.format(Locale.ENGLISH, "₹%,.2f", price);
    }

    private record Signal(int points, String text) {
    }
}
