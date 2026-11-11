package com.marketpulse.market;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyCandlesTest {

    @Test
    void aggregatesDailyCandlesIntoIsoWeeks() {
        LocalDate monday = LocalDate.of(2026, 11, 2);
        List<Candle> daily = List.of(
                new Candle(monday, 100, 105, 99, 104, 10),
                new Candle(monday.plusDays(1), 104, 110, 103, 108, 20),
                new Candle(monday.plusDays(4), 108, 109, 95, 97, 30),   // Friday
                new Candle(monday.plusDays(7), 97, 101, 96, 100, 40),   // next Monday
                new Candle(monday.plusDays(9), 100, 102, 98, 101, 50)); // next Wednesday (week in progress)

        List<Candle> weekly = WeeklyCandles.from(daily);

        assertThat(weekly).containsExactly(
                new Candle(monday.plusDays(4), 100, 110, 95, 97, 60),
                new Candle(monday.plusDays(9), 97, 102, 96, 101, 90));
    }

    @Test
    void handlesYearBoundaryUsingIsoWeeks() {
        // 31 Dec 2026 (Thu) and 1 Jan 2027 (Fri) are in the same ISO week
        List<Candle> daily = List.of(
                new Candle(LocalDate.of(2026, 12, 31), 10, 11, 9, 10, 1),
                new Candle(LocalDate.of(2027, 1, 1), 10, 12, 10, 11, 1));

        assertThat(WeeklyCandles.from(daily)).hasSize(1);
    }
}
