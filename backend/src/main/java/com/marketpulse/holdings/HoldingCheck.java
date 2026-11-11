package com.marketpulse.holdings;

import com.marketpulse.signals.Classification;

import java.time.LocalDate;
import java.util.List;

/**
 * Result of checking one holding after the close.
 *
 * @param stop       the active stop: the tighter of your stop and the trailing stop
 * @param stopSource which of the two it is, in words
 * @param reasons    why the verdict, most important first
 * @param notes      extra information that doesn't change the verdict (e.g. target reached)
 */
public record HoldingCheck(Holding holding, double price, LocalDate priceDate, double pnlPct, double pnlAmount,
                           double stop, String stopSource, Verdict verdict, int score, Classification trend,
                           List<String> reasons, List<String> notes) {
}
