package com.marketpulse.reports;

import com.marketpulse.events.MarketEvent;
import com.marketpulse.signals.TradeSignal;
import com.marketpulse.signals.TradeStyle;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The latest output of a swing, positional or BTST scan, as shown on the dashboard.
 *
 * @param market      one-line market context, e.g. "Nifty 25,255.05 (+0.84% today) · supportive for longs"
 * @param resultsSoon symbol → results date for listed setups with results inside the warning window
 * @param notes       extra warnings (overnight events, stocks excluded because results are due)
 * @param bullish     best setups across every segment; {@code sections} has them per market-cap segment
 * @param sections    large / mid / small / other Nifty 500, each with its own best and worst
 */
public record ScanReport(TradeStyle style, Instant generatedAt, LocalDate session, String market,
                         List<TradeSignal> bullish, List<TradeSignal> bearish, Map<String, LocalDate> resultsSoon,
                         List<MarketEvent> events, int scanned, List<String> failed, List<String> notes,
                         List<Section> sections) {

    /** Without sections (single-list reports). */
    public ScanReport(TradeStyle style, Instant generatedAt, LocalDate session, String market,
                      List<TradeSignal> bullish, List<TradeSignal> bearish, Map<String, LocalDate> resultsSoon,
                      List<MarketEvent> events, int scanned, List<String> failed, List<String> notes) {
        this(style, generatedAt, session, market, bullish, bearish, resultsSoon, events, scanned, failed, notes,
                List.of());
    }

    /** Snapshots saved before sections existed load with an empty list. */
    public ScanReport {
        sections = sections == null ? List.of() : sections;
    }
}
