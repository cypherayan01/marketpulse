package com.marketpulse.holdings;

import com.marketpulse.market.Candle;
import com.marketpulse.positional.PositionalCalculator;
import com.marketpulse.positional.PositionalScorer;
import com.marketpulse.positional.PositionalSnapshot;
import com.marketpulse.signals.TradeSignal;
import com.marketpulse.signals.TradeStyle;
import com.marketpulse.swing.IndicatorCalculator;
import com.marketpulse.swing.IndicatorSnapshot;
import com.marketpulse.swing.SwingScorer;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.marketpulse.signals.Money.rupees;

/**
 * Daily health check for a holding.
 * <ul>
 *   <li>EXIT - closed below the active stop: the tighter of your stop and a trailing stop
 *       (highest close since buying minus 2 ATR for swing, 3 ATR for positional).</li>
 *   <li>REVIEW - the setup has turned bearish, the trend average is broken (50-day EMA for swing,
 *       40-week average for positional), or results are due within a week.</li>
 *   <li>HOLD - otherwise.</li>
 * </ul>
 */
public class HoldingEvaluator {

    static final int RESULTS_WARNING_DAYS = 7;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);

    private final IndicatorCalculator daily = new IndicatorCalculator();
    private final SwingScorer swingScorer = new SwingScorer();
    private final PositionalCalculator weekly = new PositionalCalculator();
    private final PositionalScorer positionalScorer = new PositionalScorer();

    /** @param resultsDate upcoming results date if known, else null */
    public HoldingCheck evaluate(Holding h, List<Candle> candles, MarketReturns nifty, LocalDate resultsDate) {
        boolean positional = h.horizon() == TradeStyle.POSITIONAL;
        IndicatorSnapshot d = daily.calculate(h.symbol(), candles);
        Candle last = candles.getLast();
        double price = last.close();

        TradeSignal signal;
        boolean trendBroken;
        String trendReason;
        if (positional) {
            PositionalSnapshot w = weekly.calculate(h.symbol(), candles);
            signal = positionalScorer.score(w, nifty.return6m());
            trendBroken = price < w.sma40w();
            trendReason = "Below the 40-week average (%s) - long-term trend broken".formatted(rupees(w.sma40w()));
        } else {
            signal = swingScorer.score(d, nifty.return3m());
            trendBroken = price < d.ema50();
            trendReason = "Below the 50-day EMA (%s) - medium-term trend broken".formatted(rupees(d.ema50()));
        }

        // Trailing stop from the highest close since the purchase.
        int atrMultiple = positional ? 3 : 2;
        double highestClose = candles.stream()
                .filter(c -> !c.date().isBefore(h.buyDate()))
                .mapToDouble(Candle::close)
                .max().orElse(price);
        double trailing = highestClose - atrMultiple * d.atr14();
        boolean yourStopIsTighter = h.stopLoss() != null && h.stopLoss() >= trailing;
        double stop = yourStopIsTighter ? h.stopLoss() : trailing;
        String stopSource = yourStopIsTighter ? "your stop"
                : "trailing stop, %d ATR below the highest close since you bought".formatted(atrMultiple);

        List<String> reasons = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Verdict verdict;
        if (price < stop) {
            verdict = Verdict.EXIT;
            reasons.add("Closed below %s %s".formatted(yourStopIsTighter ? "your stop" : "the trailing stop", rupees(stop)));
            reasons.addAll(signal.bearishFactors().stream().limit(2).toList());
        } else {
            if (signal.classification().bearish()) {
                reasons.add("Setup has turned bearish (score %d/100)".formatted(signal.score()));
            }
            if (trendBroken) {
                reasons.add(trendReason);
            }
            if (resultsDate != null && !resultsDate.isBefore(last.date())
                    && ChronoUnit.DAYS.between(last.date(), resultsDate) <= RESULTS_WARNING_DAYS) {
                reasons.add("Results on %s - decide whether to hold through the numbers".formatted(DAY.format(resultsDate)));
            }
            if (reasons.isEmpty()) {
                verdict = Verdict.HOLD;
                reasons.addAll(signal.bullishFactors().stream().limit(3).toList());
            } else {
                verdict = Verdict.REVIEW;
                reasons.addAll(signal.bearishFactors().stream().limit(2).toList());
            }
        }

        if (h.target() != null && price >= h.target()) {
            notes.add("Target %s reached - consider booking some profit or trailing the stop tighter"
                    .formatted(rupees(h.target())));
        }
        if (!yourStopIsTighter && h.stopLoss() != null) {
            notes.add("Trailing stop %s is now above your stop %s".formatted(rupees(trailing), rupees(h.stopLoss())));
        }

        double pnlPct = (price / h.buyPrice() - 1) * 100;
        double pnlAmount = h.quantity() * (price - h.buyPrice());
        return new HoldingCheck(h, price, last.date(), pnlPct, pnlAmount, stop, stopSource, verdict, signal.score(),
                signal.classification(), reasons, notes);
    }
}
