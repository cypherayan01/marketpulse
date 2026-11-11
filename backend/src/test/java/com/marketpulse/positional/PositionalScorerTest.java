package com.marketpulse.positional;

import com.marketpulse.signals.Classification;
import com.marketpulse.signals.Confidence;
import com.marketpulse.signals.TradePlan;
import com.marketpulse.signals.TradeSignal;
import com.marketpulse.signals.TradeStyle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PositionalScorerTest {

    private static final double NIFTY_6M = 0.08;
    private final PositionalScorer scorer = new PositionalScorer();

    @Test
    void textbookLongTermBreakoutScores100() {
        TradeSignal signal = scorer.score(PositionalBuilder.bullishBreakout().build(), NIFTY_6M);

        assertThat(signal.style()).isEqualTo(TradeStyle.POSITIONAL);
        assertThat(signal.score()).isEqualTo(100);
        assertThat(signal.classification()).isEqualTo(Classification.STRONG_BULLISH);
        assertThat(signal.confidence()).isEqualTo(Confidence.HIGH);
        assertThat(signal.bullishFactors())
                .anyMatch(r -> r.contains("40-week average rising"))
                .anyMatch(r -> r.contains("6-month high"))
                .anyMatch(r -> r.contains("2.0x average weekly volume"))
                .anyMatch(r -> r.contains("Outperformed Nifty by 22.0% over 6 months"));
    }

    @Test
    void planUsesWeeklyAtrStopAndThreeToOneTarget() {
        // stop = max(close - 2 * weekly ATR, 8-week low) = max(460, 450) = 460; risk 40; target = 500 + 3 * 40
        TradePlan plan = scorer.score(PositionalBuilder.bullishBreakout().build(), NIFTY_6M).plan();

        assertThat(plan.stopLoss()).isCloseTo(460, within(0.001));
        assertThat(plan.target()).isCloseTo(620, within(0.001));
        assertThat(plan.riskPct()).isCloseTo(8.0, within(0.001));
    }

    @Test
    void breakoutInvalidatesOnWeeklyCloseBackBelowSixMonthHigh() {
        TradeSignal signal = scorer.score(PositionalBuilder.bullishBreakout().build(), NIFTY_6M);

        assertThat(signal.invalidation()).isEqualTo("Weekly close back below ₹490.00 (failed breakout)");
    }

    @Test
    void longTermBreakdownScoresZeroWithoutPlan() {
        TradeSignal signal = scorer.score(PositionalBuilder.bearishBreakdown().build(), NIFTY_6M);

        assertThat(signal.score()).isZero();
        assertThat(signal.classification()).isEqualTo(Classification.STRONG_BEARISH);
        assertThat(signal.plan()).isNull();
        assertThat(signal.bearishFactors())
                .anyMatch(r -> r.contains("40-week average falling"))
                .anyMatch(r -> r.contains("6-month low"))
                .anyMatch(r -> r.contains("more than 30% below 52-week high"));
    }

    @Test
    void overboughtWeeklyRsiIsAWarning() {
        PositionalBuilder b = PositionalBuilder.bullishBreakout();
        b.rsi = 85;

        TradeSignal signal = scorer.score(b.build(), NIFTY_6M);

        assertThat(signal.score()).isEqualTo(93); // 70 - 6 - 4 = 60 points -> 50 + 60 * 50/70
        assertThat(signal.bearishFactors()).anyMatch(r -> r.contains("overbought"));
    }

    @Test
    void lessThanSixtyWeeksOfHistoryMeansLowConfidence() {
        PositionalBuilder b = PositionalBuilder.bullishBreakout();
        b.weeks = 50;

        assertThat(scorer.score(b.build(), NIFTY_6M).confidence()).isEqualTo(Confidence.LOW);
    }
}
