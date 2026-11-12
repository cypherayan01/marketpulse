package com.marketpulse.btst;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
class JdbcBtstPreferencesTest {

    @Autowired private JdbcClient jdbc;
    private JdbcBtstPreferences preferences;

    @BeforeEach
    void setUp() {
        preferences = new JdbcBtstPreferences(jdbc);
    }

    @Test
    void anyPriceUntilARangeIsSaved() {
        assertThat(preferences.priceRange()).isEqualTo(PriceRange.ANY);
    }

    @Test
    void savedRangeIsReadBackAndCanBeChangedOrCleared() {
        preferences.savePriceRange(new PriceRange(100.0, 800.0));
        assertThat(preferences.priceRange()).isEqualTo(new PriceRange(100.0, 800.0));

        preferences.savePriceRange(new PriceRange(null, 1500.0));
        assertThat(preferences.priceRange()).isEqualTo(new PriceRange(null, 1500.0));

        preferences.savePriceRange(PriceRange.ANY);
        assertThat(preferences.priceRange()).isEqualTo(PriceRange.ANY);
    }
}
