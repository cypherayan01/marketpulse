package com.marketpulse.swing;

import com.marketpulse.signals.TradePlan;

import com.marketpulse.signals.Confidence;

import com.marketpulse.signals.Classification;

import com.marketpulse.signals.TradeSignal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SwingScorerTest {

    private static final double NIFTY_3M = 0.03;
    private final SwingScorer scorer = new SwingScorer();

    @Test
    void textbookBreakoutIsStrongBullishWithHighConfidence() {
        TradeSignal signal = scorer.score(SnapshotBuilder.bullishBreakout().build(), NIFTY_3M);

        assertThat(signal.classification()).isEqualTo(Classification.STRONG_BULLISH);
        assertThat(signal.score()).isEqualTo(100);
        assertThat(signal.confidence()).isEqualTo(Confidence.HIGH);
        assertThat(signal.bullishFactors()).anyMatch(r -> r.contains("20-day high"))
                .anyMatch(r -> r.contains("2.0x average volume"))
                .anyMatch(r -> r.contains("MACD"))
                .anyMatch(r -> r.contains("Outperformed Nifty by 12.0%"));
    }

    @Test
    void bullishPlanUsesAtrStopAndTwoToOneTarget() {
        // stop = max(close - 2*ATR, 10-day swing low) = max(104, 103) = 104; target = close + 2 * risk
        TradePlan plan = scorer.score(SnapshotBuilder.bullishBreakout().build(), NIFTY_3M).plan();

        assertThat(plan.entry()).isCloseTo(110, within(0.001));
        assertThat(plan.stopLoss()).isCloseTo(104, within(0.001));
        assertThat(plan.target()).isCloseTo(122, within(0.001));
        assertThat(plan.riskPct()).isCloseTo(5.45, within(0.01));
    }

    @Test
    void stopIsNeverTighterThanOneAtr() {
        SnapshotBuilder b = SnapshotBuilder.bullishBreakout();
        b.swingLow10 = 109; // support right under price would give a stop that gets hit by normal noise

        TradePlan plan = scorer.score(b.build(), NIFTY_3M).plan();

        assertThat(plan.stopLoss()).isCloseTo(107, within(0.001)); // close - 1 ATR
    }

    @Test
    void breakoutInvalidatesOnCloseBackBelowBreakoutLevel() {
        TradeSignal signal = scorer.score(SnapshotBuilder.bullishBreakout().build(), NIFTY_3M);

        assertThat(signal.invalidation()).contains("108.00").contains("failed breakout");
    }

    @Test
    void nonBreakoutBullishInvalidatesOnStop() {
        SnapshotBuilder b = SnapshotBuilder.bullishBreakout();
        b.prior20High = 115;
        b.high52w = 130;

        TradeSignal signal = scorer.score(b.build(), NIFTY_3M);

        assertThat(signal.classification()).isIn(Classification.BULLISH, Classification.STRONG_BULLISH);
        assertThat(signal.invalidation()).contains("104.00");
    }

    @Test
    void breakdownIsStrongBearishWithoutTradePlan() {
        TradeSignal signal = scorer.score(SnapshotBuilder.bearishBreakdown().build(), NIFTY_3M);

        assertThat(signal.classification()).isEqualTo(Classification.STRONG_BEARISH);
        assertThat(signal.score()).isZero();
        assertThat(signal.plan()).isNull(); // no overnight shorts in the cash market
        assertThat(signal.bearishFactors()).anyMatch(r -> r.contains("20-day low"))
                .anyMatch(r -> r.contains("Underperformed Nifty"));
    }

    @Test
    void overboughtRsiReducesScoreAndIsFlagged() {
        SnapshotBuilder b = SnapshotBuilder.bullishBreakout();
        b.prior20High = 115; b.high52w = 130; b.cross = MacdCross.NONE; // +43 points -> 50 + 43 * 50/66
        assertThat(scorer.score(b.build(), NIFTY_3M).score()).isEqualTo(83);
        b.rsi = 80;                                                       // RSI +6 becomes -4 -> +33 points

        TradeSignal overbought = scorer.score(b.build(), NIFTY_3M);

        assertThat(overbought.score()).isEqualTo(75);
        assertThat(overbought.bearishFactors()).anyMatch(r -> r.contains("overbought"));
        assertThat(overbought.bullishFactors()).noneMatch(r -> r.contains("RSI"));
    }

    @Test
    void onlyAPerfectSetupScores100SoStrongSetupsStillRankApart() {
        SnapshotBuilder b = SnapshotBuilder.bullishBreakout();
        b.high52w = 130; // lose the 52-week-high bonus: 62 of 66 bullish points

        assertThat(scorer.score(b.build(), NIFTY_3M).score()).isEqualTo(97);
    }

    @Test
    void onlyAPerfectBreakdownScoresZero() {
        SnapshotBuilder b = SnapshotBuilder.bearishBreakdown();
        b.cross = MacdCross.NONE; // 58 of 62 bearish points -> 50 - 58 * 50/62

        assertThat(scorer.score(b.build(), NIFTY_3M).score()).isEqualTo(3);
    }

    @Test
    void mixedSignalsAreNeutralWithLowConfidence() {
        SnapshotBuilder b = new SnapshotBuilder();
        b.close = 100; b.ema20 = 101; b.ema50 = 99; b.ema200 = 95;
        b.macd = 0.1; b.macdSignal = 0.2; b.cross = MacdCross.NONE; b.rsi = 48;
        b.prior20High = 103; b.prior20Low = 97; b.high52w = 120; b.return3m = 0.04;

        TradeSignal signal = scorer.score(b.build(), NIFTY_3M);

        assertThat(signal.score()).isEqualTo(53); // +4 points -> 50 + 4 * 50/66
        assertThat(signal.classification()).isEqualTo(Classification.NEUTRAL);
        assertThat(signal.confidence()).isEqualTo(Confidence.LOW);
        assertThat(signal.plan()).isNull();
    }

    @Test
    void closeBarelyAboveTheLevelIsNotABreakout() {
        SnapshotBuilder b = SnapshotBuilder.bullishBreakout();
        b.close = 108.3; // only 0.28% above the 20-day high of 108 - inside the 0.5% buffer

        TradeSignal signal = scorer.score(b.build(), NIFTY_3M);

        assertThat(signal.bullishFactors()).noneMatch(r -> r.contains("20-day high"));
        assertThat(signal.invalidation()).contains("stop loss"); // not "failed breakout"
    }

    @Test
    void closeClearlyAboveTheLevelIsABreakout() {
        SnapshotBuilder b = SnapshotBuilder.bullishBreakout();
        b.close = 108.6; // 0.56% above 108

        assertThat(scorer.score(b.build(), NIFTY_3M).bullishFactors()).anyMatch(r -> r.contains("20-day high"));
    }

    @Test
    void closeBarelyBelowTheLowIsNotABreakdown() {
        SnapshotBuilder b = SnapshotBuilder.bearishBreakdown();
        b.close = 81.8; // 0.24% below the 20-day low of 82

        assertThat(scorer.score(b.build(), NIFTY_3M).bearishFactors()).noneMatch(r -> r.contains("20-day low"));
    }

    @Test
    void separatesSupportingAndOpposingFactors() {
        TradeSignal signal = scorer.score(SnapshotBuilder.bearishBreakdown().build(), NIFTY_3M);

        assertThat(signal.bullishFactors()).isEmpty();
        assertThat(signal.bearishFactors()).contains("Below 200-day EMA (long-term downtrend)");
    }

    @Test
    void shortHistoryMeansLowConfidence() {
        SnapshotBuilder b = SnapshotBuilder.bullishBreakout();
        b.bars = 150; // 200-day EMA not reliable yet

        assertThat(scorer.score(b.build(), NIFTY_3M).confidence()).isEqualTo(Confidence.LOW);
    }
}
