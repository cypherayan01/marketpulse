package com.marketpulse.holdings;

import com.marketpulse.signals.TradeStyle;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

public class JdbcHoldingRepository implements HoldingRepository {

    private final JdbcClient jdbc;

    public JdbcHoldingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Holding add(Holding h) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                        INSERT INTO holding (symbol, quantity, buy_price, buy_date, horizon, stop_loss, target, created_at)
                        VALUES (:symbol, :quantity, :buyPrice, :buyDate, :horizon, :stopLoss, :target, :createdAt)""")
                .param("symbol", h.symbol())
                .param("quantity", h.quantity())
                .param("buyPrice", h.buyPrice())
                .param("buyDate", h.buyDate())
                .param("horizon", h.horizon().name())
                .param("stopLoss", h.stopLoss())
                .param("target", h.target())
                .param("createdAt", OffsetDateTime.ofInstant(h.createdAt(), ZoneOffset.UTC))
                .update(keys, "id");
        long id = keys.getKeyAs(Number.class).longValue();
        return new Holding(id, h.symbol(), h.quantity(), h.buyPrice(), h.buyDate(), h.horizon(), h.stopLoss(),
                h.target(), h.createdAt());
    }

    @Override
    public List<Holding> findAll() {
        return jdbc.sql("""
                        SELECT id, symbol, quantity, buy_price, buy_date, horizon, stop_loss, target, created_at
                        FROM holding ORDER BY symbol, id""")
                .query((rs, row) -> new Holding(
                        rs.getLong("id"),
                        rs.getString("symbol"),
                        rs.getInt("quantity"),
                        rs.getDouble("buy_price"),
                        rs.getObject("buy_date", LocalDate.class),
                        TradeStyle.valueOf(rs.getString("horizon")),
                        rs.getObject("stop_loss", Double.class),
                        rs.getObject("target", Double.class),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()))
                .list();
    }

    @Override
    public boolean delete(long id) {
        return jdbc.sql("DELETE FROM holding WHERE id = :id").param("id", id).update() > 0;
    }
}
