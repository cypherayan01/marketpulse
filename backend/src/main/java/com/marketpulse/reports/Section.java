package com.marketpulse.reports;

import com.marketpulse.signals.TradeSignal;
import com.marketpulse.universe.Segment;

import java.util.List;

/**
 * One market-cap segment of a scan report.
 *
 * @param indexLine the segment index's move, e.g. "Nifty 100 +0.62% today" (null if unavailable)
 * @param scanned   stocks of this segment that were scored
 */
public record Section(Segment segment, String indexLine, int scanned, List<TradeSignal> bullish,
                      List<TradeSignal> bearish) {
}
