package com.marketpulse.holdings;

import com.marketpulse.signals.TradeStyle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
class JdbcHoldingRepositoryTest {

    @Autowired
    private JdbcClient jdbc;

    @Test
    void addsListsAndDeletesHoldings() {
        JdbcHoldingRepository repository = new JdbcHoldingRepository(jdbc);

        Holding tcs = repository.add(new Holding(null, "TCS", 5, 3100.5, LocalDate.of(2026, 10, 1), TradeStyle.POSITIONAL,
                null, 3600.0, Instant.parse("2026-11-11T10:00:00Z")));
        repository.add(new Holding(null, "AXISBANK", 20, 1290, LocalDate.of(2026, 11, 3), TradeStyle.SWING,
                1240.0, null, Instant.parse("2026-11-11T10:00:00Z")));

        assertThat(tcs.id()).isNotNull();
        assertThat(repository.findAll()).extracting(Holding::symbol).containsExactly("AXISBANK", "TCS");
        Holding stored = repository.findAll().get(1);
        assertThat(stored.stopLoss()).isNull();
        assertThat(stored.target()).isEqualTo(3600.0);
        assertThat(stored.horizon()).isEqualTo(TradeStyle.POSITIONAL);

        assertThat(repository.delete(tcs.id())).isTrue();
        assertThat(repository.delete(tcs.id())).isFalse();
        assertThat(repository.findAll()).extracting(Holding::symbol).containsExactly("AXISBANK");
    }
}
