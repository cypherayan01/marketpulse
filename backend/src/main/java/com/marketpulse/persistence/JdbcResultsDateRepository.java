package com.marketpulse.persistence;

import com.marketpulse.events.MarketEvent;
import com.marketpulse.events.Source;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.LocalDate;
import java.util.List;

/** Results dates extracted from news, so the calendar keeps them across restarts. */
public class JdbcResultsDateRepository {

    private final JdbcClient jdbc;

    public JdbcResultsDateRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public synchronized void save(String symbol, LocalDate date) {
        int existing = jdbc.sql("SELECT COUNT(*) FROM news_results_date WHERE symbol = :symbol AND results_date = :date")
                .param("symbol", symbol).param("date", date).query(Integer.class).single();
        if (existing == 0) {
            jdbc.sql("INSERT INTO news_results_date (symbol, results_date) VALUES (:symbol, :date)")
                    .param("symbol", symbol).param("date", date).update();
        }
    }

    /** In the order they were found, so a later reschedule replaces the earlier date when replayed. */
    public List<MarketEvent> findAll() {
        return jdbc.sql("SELECT symbol, results_date FROM news_results_date ORDER BY id")
                .query((rs, row) -> MarketEvent.results(rs.getObject("results_date", LocalDate.class),
                        rs.getString("symbol"), Source.NEWS))
                .list();
    }
}
