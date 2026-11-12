package com.marketpulse.tracking;

import com.marketpulse.signals.Classification;
import com.marketpulse.signals.Confidence;
import com.marketpulse.signals.TradeStyle;
import com.marketpulse.universe.Segment;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

public class JdbcSignalRepository implements SignalRepository {

    private static final String COLUMNS = """
            id, style, symbol, signal_date, score, classification, confidence, entry_price, stop_price, target_price,
            status, exit_date, exit_price, return_pct, max_gain_pct, max_loss_pct, sessions_held, created_at, segment""";

    private final JdbcClient jdbc;

    public JdbcSignalRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public synchronized boolean saveIfNew(TrackedSignal s) {
        int clashes = jdbc.sql("""
                        SELECT COUNT(*) FROM tracked_signal
                        WHERE style = :style AND symbol = :symbol AND (status = 'OPEN' OR signal_date = :day)""")
                .param("style", s.style().name())
                .param("symbol", s.symbol())
                .param("day", s.signalDate())
                .query(Integer.class).single();
        if (clashes > 0) {
            return false;
        }
        jdbc.sql("""
                        INSERT INTO tracked_signal (style, symbol, signal_date, score, classification, confidence,
                            entry_price, stop_price, target_price, status, max_gain_pct, max_loss_pct, sessions_held,
                            created_at, segment)
                        VALUES (:style, :symbol, :day, :score, :classification, :confidence, :entry, :stop, :target,
                            :status, :maxGain, :maxLoss, :sessions, :createdAt, :segment)""")
                .param("style", s.style().name())
                .param("symbol", s.symbol())
                .param("day", s.signalDate())
                .param("score", s.score())
                .param("classification", s.classification().name())
                .param("confidence", s.confidence().name())
                .param("entry", s.entry())
                .param("stop", s.stop())
                .param("target", s.target())
                .param("status", s.status().name())
                .param("maxGain", s.maxGainPct())
                .param("maxLoss", s.maxLossPct())
                .param("sessions", s.sessionsHeld())
                .param("createdAt", OffsetDateTime.ofInstant(s.createdAt(), ZoneOffset.UTC))
                .param("segment", s.segment() == null ? null : s.segment().name())
                .update();
        return true;
    }

    @Override
    public List<TrackedSignal> findOpen() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM tracked_signal WHERE status = 'OPEN' ORDER BY signal_date, id")
                .query(JdbcSignalRepository::map).list();
    }

    @Override
    public List<TrackedSignal> findAll() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM tracked_signal ORDER BY signal_date DESC, id DESC")
                .query(JdbcSignalRepository::map).list();
    }

    @Override
    public void update(TrackedSignal s) {
        jdbc.sql("""
                        UPDATE tracked_signal SET status = :status, exit_date = :exitDate, exit_price = :exitPrice,
                            return_pct = :returnPct, max_gain_pct = :maxGain, max_loss_pct = :maxLoss,
                            sessions_held = :sessions
                        WHERE id = :id""")
                .param("status", s.status().name())
                .param("exitDate", s.exitDate())
                .param("exitPrice", s.exitPrice())
                .param("returnPct", s.returnPct())
                .param("maxGain", s.maxGainPct())
                .param("maxLoss", s.maxLossPct())
                .param("sessions", s.sessionsHeld())
                .param("id", s.id())
                .update();
    }

    private static TrackedSignal map(ResultSet rs, int row) throws SQLException {
        return new TrackedSignal(
                rs.getLong("id"),
                TradeStyle.valueOf(rs.getString("style")),
                rs.getString("symbol"),
                rs.getObject("signal_date", LocalDate.class),
                rs.getInt("score"),
                Classification.valueOf(rs.getString("classification")),
                Confidence.valueOf(rs.getString("confidence")),
                rs.getDouble("entry_price"),
                rs.getDouble("stop_price"),
                rs.getDouble("target_price"),
                TradeStatus.valueOf(rs.getString("status")),
                rs.getObject("exit_date", LocalDate.class),
                rs.getObject("exit_price", Double.class),
                rs.getObject("return_pct", Double.class),
                rs.getDouble("max_gain_pct"),
                rs.getDouble("max_loss_pct"),
                rs.getInt("sessions_held"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getString("segment") == null ? null : Segment.valueOf(rs.getString("segment")));
    }
}
