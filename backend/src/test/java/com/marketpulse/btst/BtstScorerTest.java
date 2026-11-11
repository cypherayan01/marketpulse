package com.marketpulse.btst;

import com.marketpulse.signals.Classification;
import com.marketpulse.signals.TradePlan;
import com.marketpulse.signals.TradeSignal;
import com.marketpulse.signals.TradeStyle;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BtstScorerTest {

    private static final double NIFTY_TODAY = 0.5;
    private final BtstScorer scorer = new BtstScorer();

    @Test
    void strongCloseWithVolumeAndBreakoutIsATopCandidate() {
        TradeSignal signal = scorer.score(BtstBuilder.strongClose().build(), NIFTY_TODAY, null);

        assertThat(signal.style()).isEqualTo(TradeStyle.BTST);
        assertThat(signal.score()).isEqualTo(100);
        assertThat(signal.classification()).isEqualTo(Classification.STRONG_BULLISH);
        assertThat(signal.bullishFactors())
                .anyMatch(r -> r.contains("near the day's high"))
                .anyMatch(r -> r.contains("Up 3.0% today"))
                .anyMatch(r -> r.contains("on pace for 2.0x average"))
                .anyMatch(r -> r.contains("Rising into the close (+0.8% in the last hour)"))
                .anyMatch(r -> r.contains("Outperforming Nifty by 2.5 points today"));
    }

    @Test
    void planUsesTightStopAndOneAndAHalfTimesTarget() {
        // stop = max(day low 99.8, price - 1 ATR = 101) = 101, kept >= 0.5 ATR below price; target = 103 + 1.5 * 2
        TradePlan plan = scorer.score(BtstBuilder.strongClose().build(), NIFTY_TODAY, null).plan();

        assertThat(plan.stopLoss()).isCloseTo(101, within(0.001));
        assertThat(plan.target()).isCloseTo(106, within(0.001));
        assertThat(plan.riskPct()).isCloseTo(1.94, within(0.01));
    }

    @Test
    void invalidationIsAboutTomorrowsOpen() {
        TradeSignal signal = scorer.score(BtstBuilder.strongClose().build(), NIFTY_TODAY, null);

        assertThat(signal.invalidation())
                .isEqualTo("Tomorrow: exit if it opens below ₹101.00 or can't hold above ₹103.00 in the first 15 minutes");
    }

    @Test
    void weakCloseIsBearishWithoutPlan() {
        TradeSignal signal = scorer.score(BtstBuilder.weakClose().build(), NIFTY_TODAY, null);

        assertThat(signal.classification().bearish()).isTrue();
        assertThat(signal.plan()).isNull();
        assertThat(signal.bearishFactors())
                .anyMatch(r -> r.contains("near the day's low"))
                .anyMatch(r -> r.contains("Fading into the close"));
    }

    @Test
    void resultsDueTomorrowRulesOutABtstTrade() {
        // even a perfect setup is never a BTST candidate with results overnight: gap risk either way
        TradeSignal risky = scorer.score(BtstBuilder.strongClose().build(), NIFTY_TODAY, LocalDate.of(2026, 11, 12));

        assertThat(risky.classification()).isEqualTo(Classification.NEUTRAL);
        assertThat(risky.plan()).isNull();
        assertThat(risky.bearishFactors()).anyMatch(r -> r.contains("Results due 12 Nov - overnight gap risk"));
    }

    @Test
    void heavyVolumeOnADownDayIsSelling() {
        TradeSignal signal = scorer.score(BtstBuilder.weakClose().build(), NIFTY_TODAY, null);

        assertThat(signal.bearishFactors()).anyMatch(r -> r.contains("Heavy selling"));
    }

    @Test
    void bigDayGainIsTreatedAsOverextended() {
        BtstBuilder b = BtstBuilder.strongClose();
        b.price = 109; b.dayHigh = 109.2; b.prior20High = 102; // +9% today

        TradeSignal signal = scorer.score(b.build(), NIFTY_TODAY, null);

        assertThat(signal.bearishFactors()).anyMatch(r -> r.contains("overextended"));
        assertThat(signal.bullishFactors()).noneMatch(r -> r.startsWith("Up 9.0% today"));
    }

    @Test
    void lightVolumeIsAWarning() {
        BtstBuilder b = BtstBuilder.strongClose();
        b.volume = 550; // on pace for ~0.6x average

        assertThat(scorer.score(b.build(), NIFTY_TODAY, null).bearishFactors())
                .anyMatch(r -> r.contains("Light volume"));
    }
}
