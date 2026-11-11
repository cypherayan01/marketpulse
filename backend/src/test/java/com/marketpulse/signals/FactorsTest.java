package com.marketpulse.signals;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FactorsTest {

    @Test
    void scalesNetPointsSoOnlyTheMaximumReaches100Or0() {
        assertThat(new Factors().add(66, "all").score(66, 62)).isEqualTo(100);
        assertThat(new Factors().add(33, "half").score(66, 62)).isEqualTo(75);
        assertThat(new Factors().add(-62, "all").score(66, 62)).isZero();
        assertThat(new Factors().add(-31, "half").score(66, 62)).isEqualTo(25);
        assertThat(new Factors().score(66, 62)).isEqualTo(50);
    }

    @Test
    void eitherAddsPositiveOrNegativePoints() {
        Factors f = new Factors().either(true, 10, "up", "down").either(false, 4, "fast", "slow");

        assertThat(f.net()).isEqualTo(6);
        assertThat(f.bullish()).containsExactly("up");
        assertThat(f.bearish()).containsExactly("slow");
    }

    @Test
    void factorsAreOrderedStrongestFirst() {
        Factors f = new Factors().add(2, "small").add(9, "big").add(-1, "tiny").add(-7, "large");

        assertThat(f.bullish()).containsExactly("big", "small");
        assertThat(f.bearish()).containsExactly("large", "tiny");
    }

    @Test
    void confidenceReflectsAgreementWithOverallDirection() {
        Factors agree = new Factors().add(5, "a").add(5, "b").add(5, "c").add(5, "d").add(-1, "e");
        Factors split = new Factors().add(5, "a").add(5, "b").add(-3, "c").add(-3, "d").add(-3, "e");

        assertThat(agree.confidence(agree.score(66, 62), true)).isEqualTo(Confidence.HIGH);   // 4/5
        assertThat(split.confidence(split.score(66, 62), true)).isEqualTo(Confidence.LOW);    // 2/5
    }

    @Test
    void confidenceIsLowWithoutReliableHistory() {
        Factors agree = new Factors().add(20, "a");

        assertThat(agree.confidence(agree.score(66, 62), false)).isEqualTo(Confidence.LOW);
    }
}
