package com.marketpulse.predictions;

import com.marketpulse.signals.Classification;
import com.marketpulse.signals.TradeStyle;
import com.marketpulse.universe.Segment;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JdbcPredictionRepository implements PredictionRepository {

    private static final String CHECKPOINT_COLUMNS = Prediction.CHECKPOINTS.stream()
            .map(k -> "r_" + k + ", n_" + k).collect(Collectors.joining(", "));
    private static final String CHECKPOINT_PARAMS = Prediction.CHECKPOINTS.stream()
            .map(k -> ":r_" + k + ", :n_" + k).collect(Collectors.joining(", "));
    private static final String COLUMNS = "id, source, style, symbol, segment, signal_date, score, classification, "
            + "call_type, price, nifty_price, news_sentiment, " + CHECKPOINT_COLUMNS
            + ", next_open_pct, next_high_pct, created_at";
    private static final String INSERT = "INSERT INTO prediction (source, style, symbol, segment, signal_date, score, "
            + "classification, call_type, price, nifty_price, news_sentiment, " + CHECKPOINT_COLUMNS
            + ", next_open_pct, next_high_pct, created_at) VALUES (:source, :style, :symbol, :segment, :signalDate, "
            + ":score, :classification, :call, :price, :niftyPrice, :news, " + CHECKPOINT_PARAMS
            + ", :nextOpen, :nextHigh, :createdAt)";
    private static final String LAST_CHECKPOINT = "r_" + Prediction.CHECKPOINTS.getLast();

    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate named;

    public JdbcPredictionRepository(JdbcClient jdbc, NamedParameterJdbcTemplate named) {
        this.jdbc = jdbc;
        this.named = named;
    }

    @Override
    public synchronized boolean saveIfNew(Prediction p) {
        int existing = jdbc.sql("""
                        SELECT COUNT(*) FROM prediction WHERE source = :source AND style = :style AND symbol = :symbol
                          AND signal_date = :day AND call_type = :call""")
                .param("source", p.source().name()).param("style", p.style().name()).param("symbol", p.symbol())
                .param("day", p.signalDate()).param("call", p.call().name())
                .query(Integer.class).single();
        if (existing > 0) {
            return false;
        }
        named.update(INSERT, params(p));
        return true;
    }

    @Override
    public synchronized void replaceReplay(List<Prediction> predictions) {
        jdbc.sql("DELETE FROM prediction WHERE source = 'REPLAY'").update();
        Map<String, Prediction> unique = new LinkedHashMap<>();
        for (Prediction p : predictions) {
            unique.putIfAbsent(p.style() + "|" + p.symbol() + "|" + p.signalDate() + "|" + p.call(), p);
        }
        MapSqlParameterSource[] batch = unique.values().stream().map(JdbcPredictionRepository::params)
                .toArray(MapSqlParameterSource[]::new);
        if (batch.length > 0) {
            named.batchUpdate(INSERT, batch);
        }
    }

    @Override
    public List<Prediction> findPending(PredictionSource source, LocalDate since) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM prediction WHERE source = :source AND signal_date >= :since AND "
                        + LAST_CHECKPOINT + " IS NULL ORDER BY signal_date, id")
                .param("source", source.name()).param("since", since)
                .query(JdbcPredictionRepository::map).list();
    }

    @Override
    public List<Prediction> findAll(PredictionSource source) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM prediction WHERE source = :source ORDER BY signal_date DESC, id")
                .param("source", source.name())
                .query(JdbcPredictionRepository::map).list();
    }

    @Override
    public List<Prediction> findByDate(PredictionSource source, LocalDate date) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM prediction WHERE source = :source AND signal_date = :day "
                        + "ORDER BY style, call_type, score DESC")
                .param("source", source.name()).param("day", date)
                .query(JdbcPredictionRepository::map).list();
    }

    @Override
    public List<LocalDate> findDates(PredictionSource source) {
        return jdbc.sql("SELECT DISTINCT signal_date FROM prediction WHERE source = :source ORDER BY signal_date DESC")
                .param("source", source.name())
                .query((rs, row) -> rs.getObject(1, LocalDate.class)).list();
    }

    @Override
    public void update(Prediction p) {
        StringBuilder sql = new StringBuilder("UPDATE prediction SET next_open_pct = :nextOpen, next_high_pct = :nextHigh");
        Prediction.CHECKPOINTS.forEach(k -> sql.append(", r_").append(k).append(" = :r_").append(k)
                .append(", n_").append(k).append(" = :n_").append(k));
        sql.append(" WHERE id = :id");
        named.update(sql.toString(), params(p).addValue("id", p.id()));
    }

    private static MapSqlParameterSource params(Prediction p) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("source", p.source().name())
                .addValue("style", p.style().name())
                .addValue("symbol", p.symbol())
                .addValue("segment", p.segment() == null ? null : p.segment().name())
                .addValue("signalDate", p.signalDate())
                .addValue("score", p.score())
                .addValue("classification", p.classification().name())
                .addValue("call", p.call().name())
                .addValue("price", p.price())
                .addValue("niftyPrice", p.niftyPrice())
                .addValue("news", p.newsSentiment())
                .addValue("nextOpen", p.nextOpenPct())
                .addValue("nextHigh", p.nextHighPct())
                .addValue("createdAt", OffsetDateTime.ofInstant(p.createdAt(), ZoneOffset.UTC));
        for (int k : Prediction.CHECKPOINTS) {
            params.addValue("r_" + k, p.returns().get(k)).addValue("n_" + k, p.niftyReturns().get(k));
        }
        return params;
    }

    private static Prediction map(ResultSet rs, int row) throws SQLException {
        Map<Integer, Double> returns = new HashMap<>();
        Map<Integer, Double> niftyReturns = new HashMap<>();
        for (int k : Prediction.CHECKPOINTS) {
            Double r = rs.getObject("r_" + k, Double.class);
            Double n = rs.getObject("n_" + k, Double.class);
            if (r != null && n != null) {
                returns.put(k, r);
                niftyReturns.put(k, n);
            }
        }
        String segment = rs.getString("segment");
        return new Prediction(
                rs.getLong("id"),
                PredictionSource.valueOf(rs.getString("source")),
                TradeStyle.valueOf(rs.getString("style")),
                rs.getString("symbol"),
                segment == null ? null : Segment.valueOf(segment),
                rs.getObject("signal_date", LocalDate.class),
                rs.getInt("score"),
                Classification.valueOf(rs.getString("classification")),
                Call.valueOf(rs.getString("call_type")),
                rs.getDouble("price"),
                rs.getDouble("nifty_price"),
                rs.getInt("news_sentiment"),
                returns, niftyReturns,
                rs.getObject("next_open_pct", Double.class),
                rs.getObject("next_high_pct", Double.class),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
