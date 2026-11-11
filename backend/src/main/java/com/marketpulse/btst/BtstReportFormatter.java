package com.marketpulse.btst;

import com.marketpulse.events.MarketEvent;
import com.marketpulse.signals.TradePlan;
import com.marketpulse.signals.TradeSignal;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static com.marketpulse.signals.Money.rupees;

/** Short, action-oriented BTST message - it has to be read and acted on before 3:30 PM. */
final class BtstReportFormatter {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private BtstReportFormatter() {
    }

    static String subject(LocalDate today, int candidates) {
        return "⚡ BTST · %s · %d candidate%s".formatted(DAY.format(today), candidates, candidates == 1 ? "" : "s");
    }

    static String body(double niftyChange, List<TradeSignal> candidates, List<MarketEvent> overnight,
                       List<String> resultsDue, int scanned, List<String> failed) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ENGLISH, "Nifty %+.2f%% today. Buy near the close (by 3:25 PM), exit tomorrow.%n",
                niftyChange));
        for (MarketEvent e : overnight) {
            sb.append("⚠️ Overnight event risk: ").append(e.title()).append(" - consider smaller size or skipping.\n");
        }
        sb.append('\n');

        if (candidates.isEmpty()) {
            sb.append("No BTST candidates today - no stock is closing strong enough to hold overnight.\n");
        }
        int n = 1;
        for (TradeSignal s : candidates) {
            TradePlan p = s.plan();
            sb.append("%d. %s - %d/100 %s · confidence %s%n".formatted(n++, s.symbol(), s.score(),
                    s.classification().label(), s.confidence()));
            sb.append(String.format(Locale.ENGLISH, "   Price %s | Stop %s (-%.1f%%) | Target %s%n",
                    rupees(p.entry()), rupees(p.stopLoss()), p.riskPct(), rupees(p.target())));
            sb.append("   Why: ").append(String.join("; ", s.bullishFactors().stream().limit(5).toList())).append('\n');
            if (!s.bearishFactors().isEmpty()) {
                sb.append("   ⚠️ Watch: ").append(String.join("; ", s.bearishFactors())).append('\n');
            }
            sb.append("   ").append(s.invalidation()).append("\n\n");
        }

        if (!resultsDue.isEmpty()) {
            sb.append("Excluded - Results due: ").append(String.join(", ", resultsDue)).append('\n');
        }
        sb.append("Scanned %d stocks".formatted(scanned));
        if (!failed.isEmpty()) {
            sb.append(" · Data unavailable: ").append(String.join(", ", failed));
        }
        sb.append("\nExit by 10:30 AM tomorrow whatever happens - BTST is an overnight trade, not an investment. "
                + "Prices may be delayed a few minutes. Not investment advice.");
        return sb.toString();
    }
}
