package com.marketpulse.btst;

import com.marketpulse.signals.Classification;
import com.marketpulse.signals.Factors;
import com.marketpulse.signals.TradePlan;
import com.marketpulse.signals.TradeSignal;
import com.marketpulse.signals.TradeStyle;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static com.marketpulse.signals.Money.rupees;

/**
 * BTST (buy near today's close, sell tomorrow) scoring, run around 3 PM. 50 is neutral.
 *
 * <pre>
 * Close      in top 20% of day's range +10 · bottom 30% -10
 * Day move   +1.5..6% +8 · +6..8% +4 · above +8% -4 (overextended) · below -1% -8
 * Volume     projected >= 1.5x average: +8 on an up day, -8 on a down day · below 0.7x -4
 * Breakout   above 20-day high (by 0.5%) +8
 * Trend      price vs 20-day EMA ±5 · 20 vs 50-day EMA ±4
 * Strength   vs Nifty today beyond ±1.5 points ±6 · RSI 55-80 +4, above 80 -4, below 45 -4
 * Close-in   last hour beyond ±0.5% ±6 · Nifty today beyond ±0.3% ±3
 * Results    due before tomorrow's session -15, and never a candidate (score capped at neutral)
 * </pre>
 */
public class BtstScorer {

    private static final double BREAKOUT_BUFFER = 0.005;
    private static final int NEUTRAL_CAP = 60;
    private static final int RELIABLE_HISTORY = 200;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
    /** close 10 + day move 8 + volume 8 + breakout 8 + trend 9 + strength 6 + RSI 4 + last hour 6 + Nifty 3 */
    static final int MAX_BULLISH_POINTS = 62;
    /** close 10 + day move 8 + volume 8 + trend 9 + strength 6 + RSI 4 + last hour 6 + Nifty 3 + results 15 */
    static final int MAX_BEARISH_POINTS = 69;

    /** @param resultsDate results due before tomorrow's session, or null */
    public TradeSignal score(BtstSnapshot s, double niftyDayChangePct, LocalDate resultsDate) {
        Factors f = new Factors();
        double day = s.dayChangePct();

        if (s.closeLocation() >= 0.8) f.add(10, "Trading near the day's high (top 20% of range)");
        else if (s.closeLocation() <= 0.3) f.add(-10, "Trading near the day's low - sellers in control");

        if (day > 8) f.add(-4, pct("Up %.1f%% today - overextended, profit-booking risk overnight", day));
        else if (day > 6) f.add(4, pct("Up %.1f%% today (strong but extended)", day));
        else if (day >= 1.5) f.add(8, pct("Up %.1f%% today", day));
        else if (day < -1) f.add(-8, pct("Down %.1f%% today", -day));

        double volume = s.projectedVolumeRatio();
        if (volume >= 1.5) {
            if (day > 0) f.add(8, pct("Volume on pace for %.1fx average", volume));
            else f.add(-8, pct("Heavy selling - volume on pace for %.1fx average", volume));
        } else if (volume < 0.7) {
            f.add(-4, pct("Light volume (%.1fx average) - weak conviction", volume));
        }

        if (s.price() > s.prior20High() * (1 + BREAKOUT_BUFFER)) {
            f.add(8, "Breaking out above 20-day high (%s)".formatted(rupees(s.prior20High())));
        }

        f.either(s.price() > s.ema20(), 5, "Above 20-day EMA", "Below 20-day EMA")
                .either(s.ema20() > s.ema50(), 4, "20-day EMA above 50-day (uptrend)", "20-day EMA below 50-day (downtrend)");

        double edge = day - niftyDayChangePct;
        if (edge > 1.5) f.add(6, pct("Outperforming Nifty by %.1f points today", edge));
        else if (edge < -1.5) f.add(-6, pct("Underperforming Nifty by %.1f points today", -edge));

        if (s.rsi14() > 80) f.add(-4, pct("RSI %.0f overheated", s.rsi14()));
        else if (s.rsi14() >= 55) f.add(4, pct("RSI %.0f strong momentum", s.rsi14()));
        else if (s.rsi14() < 45) f.add(-4, pct("RSI %.0f weak momentum", s.rsi14()));

        if (s.lastHourChangePct() > 0.5) f.add(6, pct("Rising into the close (%+.1f%% in the last hour)", s.lastHourChangePct()));
        else if (s.lastHourChangePct() < -0.5) f.add(-6, pct("Fading into the close (%+.1f%% in the last hour)", s.lastHourChangePct()));

        if (niftyDayChangePct > 0.3) f.add(3, pct("Nifty up %.1f%% - supportive market", niftyDayChangePct));
        else if (niftyDayChangePct < -0.3) f.add(-3, pct("Nifty down %.1f%% - weak market", -niftyDayChangePct));

        if (resultsDate != null) {
            f.add(-15, "Results due %s - overnight gap risk".formatted(DAY.format(resultsDate)));
        }

        int score = f.score(MAX_BULLISH_POINTS, MAX_BEARISH_POINTS);
        if (resultsDate != null) {
            score = Math.min(score, NEUTRAL_CAP); // never hold overnight into results
        }
        Classification classification = Classification.of(score);
        TradePlan plan = null;
        String invalidation = null;
        if (classification.bullish()) {
            plan = plan(s);
            invalidation = "Tomorrow: exit if it opens below %s or can't hold above %s in the first 15 minutes"
                    .formatted(rupees(plan.stopLoss()), rupees(s.price()));
        }
        return new TradeSignal(s.symbol(), TradeStyle.BTST, score, classification,
                f.confidence(score, s.barCount() >= RELIABLE_HISTORY), f.bullish(), f.bearish(), plan, invalidation,
                s.price());
    }

    /** Stop: today's low or 1 ATR below, whichever is closer, but at least 0.5 ATR away. Target: 1.5x risk. */
    private static TradePlan plan(BtstSnapshot s) {
        double entry = s.price();
        double stop = Math.min(Math.max(s.dayLow(), entry - s.atr14()), entry - 0.5 * s.atr14());
        double risk = entry - stop;
        return new TradePlan(entry, stop, entry + 1.5 * risk, risk / entry * 100);
    }

    private static String pct(String format, double value) {
        return String.format(Locale.ROOT, format, value);
    }
}
