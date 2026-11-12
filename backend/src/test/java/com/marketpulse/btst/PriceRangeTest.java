package com.marketpulse.btst;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriceRangeTest {

    @Test
    void keepsPricesInsideTheRangeIncludingTheEdges() {
        PriceRange range = new PriceRange(100.0, 800.0);

        assertThat(range.contains(100)).isTrue();
        assertThat(range.contains(450)).isTrue();
        assertThat(range.contains(800)).isTrue();
        assertThat(range.contains(99.9)).isFalse();
        assertThat(range.contains(1200)).isFalse();
    }

    @Test
    void eitherEndCanBeLeftOpen() {
        assertThat(new PriceRange(null, 500.0).contains(3)).isTrue();
        assertThat(new PriceRange(null, 500.0).contains(501)).isFalse();
        assertThat(new PriceRange(200.0, null).contains(5000)).isTrue();
        assertThat(new PriceRange(200.0, null).contains(199)).isFalse();
        assertThat(PriceRange.ANY.contains(0.5)).isTrue();
        assertThat(PriceRange.ANY.isAny()).isTrue();
    }

    @Test
    void describesItselfInPlainWords() {
        assertThat(new PriceRange(100.0, 800.0).label()).isEqualTo("₹100 - ₹800");
        assertThat(new PriceRange(null, 1500.0).label()).isEqualTo("up to ₹1,500");
        assertThat(new PriceRange(250.0, null).label()).isEqualTo("₹250 and above");
        assertThat(PriceRange.ANY.label()).isEqualTo("any price");
    }

    @Test
    void rejectsRangesThatCannotMatchAnything() {
        assertThatThrownBy(() -> new PriceRange(800.0, 100.0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Minimum");
        assertThatThrownBy(() -> new PriceRange(-5.0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PriceRange(null, 0.0)).isInstanceOf(IllegalArgumentException.class);
    }
}
