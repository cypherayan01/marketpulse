package com.marketpulse.analysis;

import com.marketpulse.holdings.HoldingCheck;
import com.marketpulse.reports.NewsEntry;
import com.marketpulse.signals.TradeSignal;
import com.marketpulse.universe.Segment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the "Analyse a stock" tab shows.
 *
 * @param segment               market-cap segment, null when the stock isn't in Nifty 500
 * @param positional            weekly-chart view, null when there's too little history ({@code positionalUnavailable} says why)
 * @param holding               keep / review / exit for a stock you own, null if you didn't say you own it
 */
public record StockAnalysis(String symbol, String name, String industry, Segment segment,
                            double price, LocalDate priceDate, double dayChangePct,
                            AnalysisVerdict verdict, String headline, List<String> reasons,
                            TradeSignal swing, TradeSignal positional, String positionalUnavailable,
                            HoldingCheck holding, LocalDate resultsDate, List<NewsEntry> news, Instant generatedAt) {
}
