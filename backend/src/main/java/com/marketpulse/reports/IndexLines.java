package com.marketpulse.reports;

import com.marketpulse.market.Candle;
import com.marketpulse.market.MarketDataClient;
import com.marketpulse.universe.Segment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One-line moves of each segment's own index, e.g. "Nifty Smallcap 100 -1.20% today". Missing data is skipped. */
public final class IndexLines {

    private static final Logger log = LoggerFactory.getLogger(IndexLines.class);
    private static final int SESSIONS_6M = 126;

    private IndexLines() {
    }

    /** Prefers the live quote (Yahoo has no daily history for some segment indices), else the last two candles. */
    public static Map<Segment, String> today(MarketDataClient market, Collection<Segment> segments) {
        Map<Segment, String> lines = new EnumMap<>(Segment.class);
        for (Segment segment : segments) {
            try {
                Double change = market.quote(segment.indexSymbol()).map(q -> q.changePct()).orElse(null);
                if (change == null) {
                    List<Candle> candles = market.dailyCandles(segment.indexSymbol());
                    if (candles.size() < 2) {
                        continue;
                    }
                    change = (candles.getLast().close() / candles.get(candles.size() - 2).close() - 1) * 100;
                }
                lines.put(segment, segment.indexName() + String.format(Locale.ENGLISH, " %+.2f%% today", change));
            } catch (RuntimeException e) {
                log.debug("No index data for {}: {}", segment, e.getMessage());
            }
        }
        return lines;
    }

    public static Map<Segment, String> sixMonths(MarketDataClient market, Collection<Segment> segments) {
        return lines(market, segments, candles -> {
            int from = Math.max(0, candles.size() - 1 - SESSIONS_6M);
            double change = (candles.getLast().close() / candles.get(from).close() - 1) * 100;
            return String.format(Locale.ENGLISH, "%+.1f%% over 6 months", change);
        });
    }

    private interface Move {
        String describe(List<Candle> candles);
    }

    private static Map<Segment, String> lines(MarketDataClient market, Collection<Segment> segments, Move move) {
        Map<Segment, String> lines = new EnumMap<>(Segment.class);
        for (Segment segment : segments) {
            try {
                List<Candle> candles = market.dailyCandles(segment.indexSymbol());
                if (candles.size() >= 2) {
                    lines.put(segment, segment.indexName() + " " + move.describe(candles));
                }
            } catch (RuntimeException e) {
                log.debug("No index data for {}: {}", segment, e.getMessage());
            }
        }
        return lines;
    }

    /** "Nifty Midcap 100 stocks: median +4.2% over 6 months", or null without data. */
    public static String medianOf(Segment segment, List<Double> returns6m) {
        if (returns6m.isEmpty()) {
            return null;
        }
        List<Double> sorted = returns6m.stream().sorted().toList();
        int mid = sorted.size() / 2;
        double median = sorted.size() % 2 == 1 ? sorted.get(mid) : (sorted.get(mid - 1) + sorted.get(mid)) / 2;
        return String.format(Locale.ENGLISH, "%s stocks: median %+.1f%% over 6 months", segment.indexName(), median * 100);
    }

    /** More than this share of stocks without data means the scan itself is unreliable (network or source problem). */
    private static final double INCOMPLETE_SHARE = 0.10;

    /** "⚠️ Data incomplete: 135 of 500 stocks..." when too many stocks are missing, otherwise empty. */
    public static String incompleteWarning(int failed, int total) {
        if (total == 0 || (double) failed / total <= INCOMPLETE_SHARE) {
            return "";
        }
        return "⚠️ Data incomplete: %d of %d stocks could not be fetched (network or data problem) - "
                .formatted(failed, total) + "the lists below may be missing stocks. Re-run the scan to complete it.";
    }

    /** Lists a few missing stocks by name; many (new listings, demergers) become one count. */
    public static String skipped(List<String> failed) {
        if (failed.isEmpty()) {
            return "";
        }
        return failed.size() <= 5
                ? "Data unavailable: " + String.join(", ", failed)
                : failed.size() + " skipped (new listings or no data)";
    }
}
