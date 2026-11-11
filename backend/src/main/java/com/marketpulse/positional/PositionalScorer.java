package com.marketpulse.positional;

import com.marketpulse.signals.Classification;
import com.marketpulse.signals.Factors;
import com.marketpulse.signals.TradePlan;
import com.marketpulse.signals.TradeSignal;
import com.marketpulse.signals.TradeStyle;

import java.util.Locale;

import static com.marketpulse.signals.Money.rupees;

/**
 * Rule-based positional (1-6 month) scoring on the weekly chart. 50 is neutral.
 *
 * <pre>
 * Trend      close vs 40-week avg ±10 · 40-week avg rising/falling ±8 · 10 vs 30-week EMA ±8 · close vs 10-week EMA ±4
 * Momentum   weekly MACD vs signal ±6 · weekly RSI zone -6..+6
 * Structure  6-month breakout/breakdown ±8, ±4 more on >= 1.5x weekly volume
 *            within 10% of 52-week high +6 · more than 30% below it -6
 * Strength   6-month return vs Nifty beyond ±10 points: ±10 (leaders tend to keep leading)
 * </pre>
 */
public class PositionalScorer {

    private static final double VOLUME_CONFIRMATION = 1.5;
    private static final double RELATIVE_STRENGTH_EDGE = 0.10;
    private static final int RELIABLE_WEEKS = 60;
    private static final double TARGET_MULTIPLE = 3; // trends run longer on this horizon
    /** trend 30 + MACD 6 + RSI 6 + breakout 12 + near 52-week high 6 + strength 10 */
    static final int MAX_BULLISH_POINTS = 70;
    /** trend 30 + MACD 6 + RSI 6 + breakdown 12 + far below 52-week high 6 + strength 10 */
    static final int MAX_BEARISH_POINTS = 70;

    public TradeSignal score(PositionalSnapshot s, double niftyReturn6m) {
        Factors f = new Factors()
                .either(s.close() > s.sma40w(), 10,
                        "Above 40-week average (long-term uptrend)", "Below 40-week average (long-term downtrend)")
                .either(s.sma40w() > s.sma40wFourWeeksAgo(), 8, "40-week average rising", "40-week average falling")
                .either(s.ema10w() > s.ema30w(), 8,
                        "10-week EMA above 30-week (intermediate uptrend)", "10-week EMA below 30-week (intermediate downtrend)")
                .either(s.close() > s.ema10w(), 4, "Price above 10-week EMA", "Price below 10-week EMA")
                .either(s.macd() > s.macdSignal(), 6, "Weekly MACD above signal line", "Weekly MACD below signal line");
        rsi(f, s.rsi14w());

        double volumeRatio = s.avgVolume10w() > 0 ? s.volume() / s.avgVolume10w() : 0;
        boolean volumeConfirmed = volumeRatio >= VOLUME_CONFIRMATION;
        if (s.close() > s.prior26wHigh()) {
            f.add(8, "Broke above 6-month high (%s)".formatted(rupees(s.prior26wHigh())));
            if (volumeConfirmed) {
                f.add(4, String.format(Locale.ROOT, "Breakout on %.1fx average weekly volume", volumeRatio));
            }
        } else if (s.close() < s.prior26wLow()) {
            f.add(-8, "Broke below 6-month low (%s)".formatted(rupees(s.prior26wLow())));
            if (volumeConfirmed) {
                f.add(-4, String.format(Locale.ROOT, "Breakdown on %.1fx average weekly volume", volumeRatio));
            }
        }
        if (s.close() >= 0.9 * s.high52w()) {
            f.add(6, "Within 10% of 52-week high");
        } else if (s.close() < 0.7 * s.high52w()) {
            f.add(-6, "Price more than 30% below 52-week high");
        }

        double edge = s.return6m() - niftyReturn6m;
        if (edge > RELATIVE_STRENGTH_EDGE) {
            f.add(10, String.format(Locale.ROOT, "Outperformed Nifty by %.1f%% over 6 months", edge * 100));
        } else if (edge < -RELATIVE_STRENGTH_EDGE) {
            f.add(-10, String.format(Locale.ROOT, "Underperformed Nifty by %.1f%% over 6 months", -edge * 100));
        }

        int score = f.score(MAX_BULLISH_POINTS, MAX_BEARISH_POINTS);
        Classification classification = Classification.of(score);
        TradePlan plan = null;
        String invalidation = null;
        if (classification.bullish()) {
            plan = plan(s);
            invalidation = s.close() > s.prior26wHigh()
                    ? "Weekly close back below %s (failed breakout)".formatted(rupees(s.prior26wHigh()))
                    : "Weekly close below %s (stop loss)".formatted(rupees(plan.stopLoss()));
        }
        return new TradeSignal(s.symbol(), TradeStyle.POSITIONAL, score, classification,
                f.confidence(score, s.weeks() >= RELIABLE_WEEKS), f.bullish(), f.bearish(), plan, invalidation, s.close());
    }

    private static void rsi(Factors f, double rsi) {
        String value = String.format(Locale.ROOT, "%.0f", rsi);
        if (rsi >= 80) f.add(-4, "Weekly RSI " + value + " overbought - consider waiting for a pullback");
        else if (rsi >= 60) f.add(6, "Weekly RSI " + value + " strong momentum");
        else if (rsi >= 50) f.add(3, "Weekly RSI " + value + " positive momentum");
        else if (rsi >= 40) f.add(-3, "Weekly RSI " + value + " weak momentum");
        else f.add(-6, "Weekly RSI " + value + " bearish momentum");
    }

    /** Stop: the tighter of 2 weekly ATR and the 8-week low, never closer than 1 weekly ATR. Target: 3x risk. */
    private static TradePlan plan(PositionalSnapshot s) {
        double entry = s.close();
        double stop = Math.min(Math.max(entry - 2 * s.atr10w(), s.swingLow8w()), entry - s.atr10w());
        double risk = entry - stop;
        return new TradePlan(entry, stop, entry + TARGET_MULTIPLE * risk, risk / entry * 100);
    }
}
