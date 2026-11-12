package com.marketpulse.btst;

import com.marketpulse.events.MarketEvent;
import com.marketpulse.reports.IndexLines;
import com.marketpulse.reports.Section;
import com.marketpulse.signals.TradePlan;
import com.marketpulse.signals.TradeSignal;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static com.marketpulse.signals.Money.rupees;

/** Short, action-oriented BTST message per market-cap segment - read and acted on before 3:30 PM. */
final class BtstReportFormatter {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private BtstReportFormatter() {
    }

    static String subject(LocalDate today, int candidates) {
        return "⚡ BTST · %s · %d candidate%s".formatted(DAY.format(today), candidates, candidates == 1 ? "" : "s");
    }

    static String body(double niftyChange, List<Section> sections, List<MarketEvent> overnight,
                       List<String> resultsDue, int scanned, List<String> failed) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ENGLISH, "Nifty %+.2f%% today. Buy near the close (by 3:25 PM), exit tomorrow.%n",
                niftyChange));
        String incomplete = IndexLines.incompleteWarning(failed.size(), scanned + failed.size());
        if (!incomplete.isEmpty()) {
            sb.append(incomplete).append('\n');
        }
        for (MarketEvent e : overnight) {
            sb.append("⚠️ Overnight event risk: ").append(e.title()).append(" - consider smaller size or skipping.\n");
        }

        if (sections.isEmpty()) {
            sb.append("\nNo BTST candidates today - no stock is closing strong enough to hold overnight.\n");
        }
        for (Section section : sections) {
            String index = section.indexLine() != null ? section.indexLine() : section.segment().indexName();
            sb.append("\n%s %s · %s%n".formatted(section.segment().icon(),
                    section.segment().title().toUpperCase(Locale.ENGLISH), index));
            int n = 1;
            for (TradeSignal s : section.bullish()) {
                TradePlan p = s.plan();
                sb.append("%d. %s %d · confidence %s%n".formatted(n++, s.symbol(), s.score(), s.confidence()));
                sb.append(String.format(Locale.ENGLISH, "   Price %s · Stop %s (-%.1f%%) · Target %s%n",
                        rupees(p.entry()), rupees(p.stopLoss()), p.riskPct(), rupees(p.target())));
                sb.append("   Why: ").append(String.join("; ", s.bullishFactors().stream().limit(4).toList())).append('\n');
                if (!s.bearishFactors().isEmpty()) {
                    sb.append("   ⚠️ ").append(String.join("; ", s.bearishFactors())).append('\n');
                }
                sb.append("   ✋ ").append(s.invalidation()).append('\n');
            }
        }

        sb.append('\n');
        if (!resultsDue.isEmpty()) {
            sb.append("Excluded - Results due: ").append(String.join(", ", resultsDue)).append('\n');
        }
        sb.append("Scanned %d stocks".formatted(scanned));
        String skipped = IndexLines.skipped(failed);
        if (!skipped.isEmpty()) {
            sb.append(" · ").append(skipped);
        }
        sb.append("\nExit by 10:30 AM tomorrow whatever happens - BTST is an overnight trade, not an investment. "
                + "Prices may be delayed a few minutes. Not investment advice.");
        return sb.toString();
    }
}
