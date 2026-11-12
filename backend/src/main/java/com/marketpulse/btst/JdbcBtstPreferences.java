package com.marketpulse.btst;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

/** Stores BTST preferences as name/value rows in {@code app_setting}. */
public class JdbcBtstPreferences implements BtstPreferences {

    static final String MIN = "btst.min_price";
    static final String MAX = "btst.max_price";

    private final JdbcClient jdbc;

    public JdbcBtstPreferences(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PriceRange priceRange() {
        Map<String, String> values = jdbc.sql("SELECT name, setting_value FROM app_setting WHERE name IN (:min, :max)")
                .param("min", MIN).param("max", MAX)
                .query((rs, n) -> Map.entry(rs.getString(1), rs.getString(2)))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new PriceRange(number(values.get(MIN)), number(values.get(MAX)));
    }

    @Override
    @Transactional
    public synchronized void savePriceRange(PriceRange range) {
        save(MIN, range.minPrice());
        save(MAX, range.maxPrice());
    }

    private void save(String name, Double value) {
        jdbc.sql("DELETE FROM app_setting WHERE name = :name").param("name", name).update();
        if (value != null) {
            jdbc.sql("INSERT INTO app_setting (name, setting_value) VALUES (:name, :value)")
                    .param("name", name).param("value", value.toString()).update();
        }
    }

    private static Double number(String value) {
        return value == null ? null : Double.valueOf(value);
    }
}
