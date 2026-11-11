package com.marketpulse.tracking;

import com.marketpulse.signals.Classification;
import com.marketpulse.signals.Confidence;
import com.marketpulse.signals.TradeStyle;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A recommended setup treated as a paper trade: bought at {@code entry} on {@code signalDate}, then followed
 * until the stop, the target or the end of the holding period.
 *
 * @param id         database id, null until saved
 * @param returnPct  realised return in percent, set once closed
 * @param maxGainPct best move in our favour while open (high vs entry), in percent
 * @param maxLossPct worst move against us while open (low vs entry), in percent
 */
public record TrackedSignal(Long id, TradeStyle style, String symbol, LocalDate signalDate, int score,
                            Classification classification, Confidence confidence,
                            double entry, double stop, double target,
                            TradeStatus status, LocalDate exitDate, Double exitPrice, Double returnPct,
                            double maxGainPct, double maxLossPct, int sessionsHeld, Instant createdAt) {

    public static TrackedSignal open(TradeStyle style, String symbol, LocalDate signalDate, int score,
                                     Classification classification, Confidence confidence,
                                     double entry, double stop, double target, Instant createdAt) {
        return new TrackedSignal(null, style, symbol, signalDate, score, classification, confidence, entry, stop,
                target, TradeStatus.OPEN, null, null, null, 0, 0, 0, createdAt);
    }

    public TrackedSignal withProgress(int sessions, double maxGain, double maxLoss) {
        return new TrackedSignal(id, style, symbol, signalDate, score, classification, confidence, entry, stop, target,
                TradeStatus.OPEN, null, null, null, maxGain, maxLoss, sessions, createdAt);
    }

    public TrackedSignal close(TradeStatus outcome, LocalDate date, double price, int sessions, double maxGain,
                               double maxLoss) {
        return new TrackedSignal(id, style, symbol, signalDate, score, classification, confidence, entry, stop, target,
                outcome, date, price, (price / entry - 1) * 100, maxGain, maxLoss, sessions, createdAt);
    }

    public TrackedSignal withId(long newId) {
        return new TrackedSignal(newId, style, symbol, signalDate, score, classification, confidence, entry, stop,
                target, status, exitDate, exitPrice, returnPct, maxGainPct, maxLossPct, sessionsHeld, createdAt);
    }
}
