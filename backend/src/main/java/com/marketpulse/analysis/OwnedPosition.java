package com.marketpulse.analysis;

import com.marketpulse.signals.TradeStyle;

import java.time.LocalDate;

/** "I already own this": what's needed to say keep or exit. {@code stopLoss} is optional. */
public record OwnedPosition(int quantity, double buyPrice, LocalDate buyDate, TradeStyle horizon, Double stopLoss) {
}
