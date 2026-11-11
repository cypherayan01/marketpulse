package com.marketpulse.holdings;

import com.marketpulse.signals.TradeStyle;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A stock you actually own.
 *
 * @param horizon  SWING (judged on the daily chart) or POSITIONAL (weekly chart)
 * @param stopLoss your own stop, optional - a trailing stop is always computed as well
 * @param target   your target, optional
 */
public record Holding(Long id, String symbol, int quantity, double buyPrice, LocalDate buyDate, TradeStyle horizon,
                      Double stopLoss, Double target, Instant createdAt) {
}
