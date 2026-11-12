package com.marketpulse.universe;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The stocks to scan, each with its market-cap segment: Nifty 100 = large, Nifty Midcap 100 = mid,
 * Nifty Smallcap 100 = small, and the rest of Nifty 500 = other.
 */
public final class Universe {

    private final Map<String, Stock> stocks;

    private Universe(Map<String, Stock> stocks) {
        this.stocks = stocks;
    }

    public static Universe of(List<IndexMember> large, List<IndexMember> mid, List<IndexMember> small,
                              List<IndexMember> all) {
        Map<String, Stock> stocks = new LinkedHashMap<>();
        addAll(stocks, large, Segment.LARGE);
        addAll(stocks, mid, Segment.MID);
        addAll(stocks, small, Segment.SMALL);
        addAll(stocks, all, Segment.OTHER);
        return new Universe(stocks);
    }

    /** A plain list of symbols (tests, custom watchlists): everything in {@link Segment#OTHER}. */
    public static Universe unsegmented(List<String> symbols) {
        Map<String, Stock> stocks = new LinkedHashMap<>();
        symbols.forEach(s -> stocks.putIfAbsent(key(s), new Stock(s, s, "", Segment.OTHER)));
        return new Universe(stocks);
    }

    /** The NSE index lists bundled under resources/indices (refresh them after each March/September rebalance). */
    public static Universe loadBundled() {
        return of(read("nifty100"), read("niftymidcap100"), read("niftysmallcap100"), read("nifty500"));
    }

    public List<String> symbols() {
        return stocks.values().stream().map(Stock::symbol).toList();
    }

    public List<Stock> stocks(Segment segment) {
        return stocks.values().stream().filter(s -> s.segment() == segment).toList();
    }

    public Optional<Stock> find(String symbol) {
        return Optional.ofNullable(stocks.get(key(symbol)));
    }

    public Segment segmentOf(String symbol) {
        return find(symbol).map(Stock::segment).orElse(Segment.OTHER);
    }

    private static void addAll(Map<String, Stock> stocks, List<IndexMember> members, Segment segment) {
        members.forEach(m -> stocks.putIfAbsent(key(m.symbol()), new Stock(m.symbol(), m.name(), m.industry(), segment)));
    }

    private static String key(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private static List<IndexMember> read(String name) {
        String path = "/indices/" + name + ".csv";
        try (InputStream in = Universe.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing bundled index list " + path);
            }
            return new ArrayList<>(IndexListParser.parse(in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
