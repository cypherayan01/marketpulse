package com.marketpulse.swing;

import com.marketpulse.signals.Classification;
import com.marketpulse.signals.Factors;
import com.marketpulse.signals.TradePlan;
import com.marketpulse.signals.TradeSignal;
import com.marketpulse.signals.TradeStyle;

import java.util.Locale;

import static com.marketpulse.signals.Money.rupees;

/**
 * Rule-based swing-trade scoring on the daily chart. Starts at 50 (neutral); each signal adds or subtracts points.
 *
 * <pre>
 * Trend      close vs 200 EMA ±10 · 20 EMA vs 50 EMA ±8 · close vs 20 EMA ±5
 * Momentum   MACD vs signal ±6 · fresh MACD crossover ±4 · RSI zone -6..+6
 * Price      20-day breakout/breakdown ±10, +-5 more on >= 1.5x volume · within 5% of 52-week high +4
 *            (the close must clear the level by 0.5% to count - marginal pokes are often false breakouts)
 * Strength   3-month return vs Nifty beyond ±5 points: ±8
 * </pre>
 * Net points are scaled so only a perfect bullish setup scores 100 and only a perfect breakdown scores 0.
 */
public class SwingScorer {

    private static final double VOLUME_CONFIRMATION = 1.5;
    static final double BREAKOUT_BUFFER = 0.005;
    private static final double RELATIVE_STRENGTH_EDGE = 0.05;
    private static final int RELIABLE_HISTORY = 200;
    /** Most points the rules can add: trend 23 + MACD 10 + RSI 6 + breakout 15 + 52-week high 4 + strength 8. */
    static final int MAX_BULLISH_POINTS = 66;
    /** Most points the rules can subtract: trend 23 + MACD 10 + RSI 6 + breakdown 15 + strength 8. */
    static final int MAX_BEARISH_POINTS = 62;

    public TradeSignal score(IndicatorSnapshot s, double niftyReturn3m) {
        Factors f = new Factors();
        // Trend. A recent listing has no 200-day EMA yet (NaN) - leave it out rather than read it as "below".
        if (!Double.isNaN(s.ema200())) {
            f.either(s.close() > s.ema200(), 10,
                    "Above 200-day EMA (long-term uptrend)", "Below 200-day EMA (long-term downtrend)");
        }
        f.either(s.ema20() > s.ema50(), 8,
                        "20-day EMA above 50-day (medium-term uptrend)", "20-day EMA below 50-day (medium-term downtrend)")
                .either(s.close() > s.ema20(), 5, "Price above 20-day EMA", "Price below 20-day EMA")
                // Momentum
                .either(s.macd() > s.macdSignal(), 6, "MACD above signal line", "MACD below signal line");
        if (s.macdCross() == MacdCross.BULLISH) {
            f.add(4, "Fresh MACD bullish crossover");
        } else if (s.macdCross() == MacdCross.BEARISH) {
            f.add(-4, "Fresh MACD bearish crossover");
        }
        rsi(f, s.rsi14());

        // Price action
        double volumeRatio = s.avgVolume20() > 0 ? s.volume() / s.avgVolume20() : 0;
        boolean volumeConfirmed = volumeRatio >= VOLUME_CONFIRMATION;
        boolean brokeOut = s.close() > s.prior20High() * (1 + BREAKOUT_BUFFER);
        boolean brokeDown = s.close() < s.prior20Low() * (1 - BREAKOUT_BUFFER);
        if (brokeOut) {
            f.add(10, "Broke above 20-day high (%s)".formatted(rupees(s.prior20High())));
            if (volumeConfirmed) {
                f.add(5, String.format(Locale.ROOT, "Breakout on %.1fx average volume", volumeRatio));
            }
        } else if (brokeDown) {
            f.add(-10, "Broke below 20-day low (%s)".formatted(rupees(s.prior20Low())));
            if (volumeConfirmed) {
                f.add(-5, String.format(Locale.ROOT, "Breakdown on %.1fx average volume", volumeRatio));
            }
        }
        if (s.close() >= 0.95 * s.high52w()) {
            f.add(4, "Within 5% of 52-week high");
        }

        // Relative strength
        double edge = s.return3m() - niftyReturn3m;
        if (edge > RELATIVE_STRENGTH_EDGE) {
            f.add(8, String.format(Locale.ROOT, "Outperformed Nifty by %.1f%% over 3 months", edge * 100));
        } else if (edge < -RELATIVE_STRENGTH_EDGE) {
            f.add(-8, String.format(Locale.ROOT, "Underperformed Nifty by %.1f%% over 3 months", -edge * 100));
        }

        int score = f.score(MAX_BULLISH_POINTS, MAX_BEARISH_POINTS);
        Classification classification = Classification.of(score);
        TradePlan plan = null;
        String invalidation = null;
        if (classification.bullish()) {
            plan = plan(s);
            invalidation = brokeOut
                    ? "Daily close back below %s (failed breakout)".formatted(rupees(s.prior20High()))
                    : "Daily close below %s (stop loss)".formatted(rupees(plan.stopLoss()));
        }
        return new TradeSignal(s.symbol(), TradeStyle.SWING, score, classification,
                f.confidence(score, s.barCount() >= RELIABLE_HISTORY), f.bullish(), f.bearish(), plan, invalidation,
                s.close());
    }

    private static void rsi(Factors f, double rsi) {
        String value = String.format(Locale.ROOT, "%.0f", rsi);
        if (rsi >= 75) f.add(-4, "RSI " + value + " overbought - pullback risk, avoid chasing");
        else if (rsi >= 65) f.add(2, "RSI " + value + " strong but getting stretched");
        else if (rsi >= 50) f.add(6, "RSI " + value + " healthy momentum");
        else if (rsi >= 40) f.add(-3, "RSI " + value + " weak momentum");
        else if (rsi >= 30) f.add(-6, "RSI " + value + " bearish momentum");
        else f.add(-2, "RSI " + value + " oversold - wait for a reversal before buying");
    }

    /** Stop: the tighter of 2 ATR and the 10-day swing low, but never closer than 1 ATR. Target: 2x the risk. */
    private static TradePlan plan(IndicatorSnapshot s) {
        double entry = s.close();
        double stop = Math.min(Math.max(entry - 2 * s.atr14(), s.swingLow10()), entry - s.atr14());
        double risk = entry - stop;
        return new TradePlan(entry, stop, entry + 2 * risk, risk / entry * 100);
    }
}
