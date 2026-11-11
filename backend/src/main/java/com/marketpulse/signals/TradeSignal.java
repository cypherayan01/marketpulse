package com.marketpulse.signals;

import java.util.List;

/**
 * Result of scoring one stock for a trading style.
 *
 * @param bullishFactors signals in favour of going long, strongest first
 * @param bearishFactors signals against it (for a bullish setup these are the warnings), strongest first
 * @param plan           only present for bullish setups (overnight shorts are not possible in the cash market)
 * @param invalidation   what would prove the bullish idea wrong; only present for bullish setups
 */
public record TradeSignal(String symbol, TradeStyle style, int score, Classification classification,
                          Confidence confidence, List<String> bullishFactors, List<String> bearishFactors,
                          TradePlan plan, String invalidation, double close) {
}
