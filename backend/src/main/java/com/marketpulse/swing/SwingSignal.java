package com.marketpulse.swing;

import java.util.List;

/**
 * Result of scoring one stock for a swing trade.
 *
 * @param bullishFactors signals in favour of going long, strongest first
 * @param bearishFactors signals against it (for a bullish setup these are the warnings), strongest first
 * @param plan         only present for bullish setups (overnight shorts are not possible in the cash market)
 * @param invalidation what would prove the bullish idea wrong; only present for bullish setups
 */
public record SwingSignal(String symbol, int score, Classification classification, Confidence confidence,
                          List<String> bullishFactors, List<String> bearishFactors, TradePlan plan, String invalidation, IndicatorSnapshot snapshot) {
}
