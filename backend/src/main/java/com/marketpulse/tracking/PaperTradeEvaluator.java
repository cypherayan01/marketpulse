package com.marketpulse.tracking;

import com.marketpulse.market.Candle;
import com.marketpulse.signals.TradeStyle;

import java.util.List;

/**
 * Replays daily candles after the signal day to see how a paper trade would have ended.
 *
 * <ul>
 *   <li>Stop first: if a day touches both the stop and the target we can't know which came first, so we
 *       assume the stop (conservative).</li>
 *   <li>Gaps: opening beyond the stop or target fills at the open, not at the level.</li>
 *   <li>Holding period: swing 15 sessions, positional 120; then closed at that day's close.</li>
 *   <li>BTST: closed at the next session's open (the "exit by 10:30" rule, approximated with daily data).</li>
 * </ul>
 */
public class PaperTradeEvaluator {

    static final int SWING_SESSIONS = 15;
    static final int POSITIONAL_SESSIONS = 120;

    public TrackedSignal evaluate(TrackedSignal trade, List<Candle> candles) {
        if (trade.status().closed()) {
            return trade;
        }
        List<Candle> after = candles.stream().filter(c -> c.date().isAfter(trade.signalDate())).toList();
        if (after.isEmpty()) {
            return trade;
        }
        return trade.style() == TradeStyle.BTST ? btst(trade, after.getFirst()) : hold(trade, after);
    }

    private static TrackedSignal btst(TrackedSignal t, Candle next) {
        double move = pct(next.open(), t.entry());
        TradeStatus outcome = next.open() >= t.target() ? TradeStatus.TARGET_HIT
                : next.open() <= t.stop() ? TradeStatus.STOPPED
                : TradeStatus.EXPIRED;
        return t.close(outcome, next.date(), next.open(), 1, Math.max(move, 0), Math.min(move, 0));
    }

    private static TrackedSignal hold(TrackedSignal t, List<Candle> after) {
        int horizon = t.style() == TradeStyle.POSITIONAL ? POSITIONAL_SESSIONS : SWING_SESSIONS;
        double maxGain = 0;
        double maxLoss = 0;
        int sessions = 0;
        for (Candle c : after) {
            sessions++;
            maxGain = Math.max(maxGain, pct(c.high(), t.entry()));
            maxLoss = Math.min(maxLoss, pct(c.low(), t.entry()));
            if (c.low() <= t.stop()) {
                return t.close(TradeStatus.STOPPED, c.date(), Math.min(c.open(), t.stop()), sessions, maxGain, maxLoss);
            }
            if (c.high() >= t.target()) {
                return t.close(TradeStatus.TARGET_HIT, c.date(), Math.max(c.open(), t.target()), sessions, maxGain, maxLoss);
            }
            if (sessions >= horizon) {
                return t.close(TradeStatus.EXPIRED, c.date(), c.close(), sessions, maxGain, maxLoss);
            }
        }
        return t.withProgress(sessions, maxGain, maxLoss);
    }

    private static double pct(double price, double entry) {
        return (price / entry - 1) * 100;
    }
}
