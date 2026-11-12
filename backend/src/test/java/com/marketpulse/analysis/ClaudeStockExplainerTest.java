package com.marketpulse.analysis;

import com.marketpulse.signals.Classification;
import com.marketpulse.signals.Confidence;
import com.marketpulse.signals.TradePlan;
import com.marketpulse.signals.TradeSignal;
import com.marketpulse.signals.TradeStyle;
import com.marketpulse.universe.Segment;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeStockExplainerTest {

    @Test
    void promptCarriesTheComputedFactsOnly() {
        TradeSignal swing = new TradeSignal("TCS", TradeStyle.SWING, 86, Classification.STRONG_BULLISH, Confidence.HIGH,
                List.of("Broke above 20-day high (₹3,250.00)"), List.of("RSI 77 overbought"),
                new TradePlan(3300, 3150, 3600, 4.5), "Daily close back below ₹3,250.00 (failed breakout)", 3300);
        StockAnalysis a = new StockAnalysis("TCS", "Tata Consultancy Services Ltd.", "Information Technology",
                Segment.LARGE, 3300, LocalDate.of(2026, 11, 12), 1.2, AnalysisVerdict.BUY_SWING,
                "Buy for a swing trade only", List.of("Results on 16 Nov"), swing, null,
                "only 30 weeks of history", null, LocalDate.of(2026, 11, 16), List.of(), Instant.EPOCH);

        String prompt = ClaudeStockExplainer.buildPrompt(a);

        assertThat(prompt)
                .contains("TCS (Tata Consultancy Services Ltd.) · Information Technology · Large cap")
                .contains("Price ₹3,300.00 (+1.20% on 12 Nov)")
                .contains("Verdict for a new buyer: BUY_SWING")
                .contains("SWING: 86/100 STRONG BULLISH")
                .contains("Entry ₹3,300.00, stop ₹3,150.00, target ₹3,600.00")
                .contains("Broke above 20-day high").contains("RSI 77 overbought")
                .contains("POSITIONAL: not available - only 30 weeks of history")
                .contains("Results due 16 Nov")
                .contains("You don't own it");
    }
}
