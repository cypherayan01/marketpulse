package com.marketpulse.persistence;

import com.marketpulse.events.MarketEvent;
import com.marketpulse.events.Source;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
class JdbcResultsDateRepositoryTest {

    @Autowired
    private JdbcClient jdbc;

    @Test
    void remembersResultsDatesFoundInNewsInTheOrderTheyArrived() {
        JdbcResultsDateRepository repository = new JdbcResultsDateRepository(jdbc);
        repository.save("TCS", LocalDate.of(2027, 1, 9));
        repository.save("INFY", LocalDate.of(2027, 1, 15));
        repository.save("TCS", LocalDate.of(2027, 1, 9)); // duplicate ignored
        repository.save("TCS", LocalDate.of(2027, 1, 12)); // rescheduled - kept after the original

        assertThat(repository.findAll()).containsExactly(
                MarketEvent.results(LocalDate.of(2027, 1, 9), "TCS", Source.NEWS),
                MarketEvent.results(LocalDate.of(2027, 1, 15), "INFY", Source.NEWS),
                MarketEvent.results(LocalDate.of(2027, 1, 12), "TCS", Source.NEWS));
    }
}
