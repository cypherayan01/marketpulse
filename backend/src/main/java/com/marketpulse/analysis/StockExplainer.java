package com.marketpulse.analysis;

/** Turns an analysis into a short plain-English explanation. */
@FunctionalInterface
public interface StockExplainer {
    String explain(StockAnalysis analysis);
}
