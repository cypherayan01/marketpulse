package com.marketpulse.positional;

import com.marketpulse.events.MarketEvent;
import com.marketpulse.reports.IndexLines;
import com.marketpulse.reports.Section;
import com.marketpulse.signals.TradePlan;
import com.marketpulse.signals.TradeSignal;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.marketpulse.signals.Money.rupees;

/** Plain-text weekly positional outlook, one section per market-cap segment. */
final class PositionalReportFormatter {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
    private static final DateTimeFormatter EVENT_DAY = DateTimeFormatter.ofPattern("EEE dd MMM", Locale.ENGLISH);

    private PositionalReportFormatter() {
    }

    static String subject(LocalDate weekEnding, int bullish, int bearish) {
        return "🗓️ Positional outlook · week ending %s · %d bullish, %d to avoid"
                .formatted(DAY.format(weekEnding), bullish, bearish);
    }

    static String body(PositionalSnapshot index, List<Section> sections, Map<String, MarketEvent> resultsSoon,
                       List<MarketEvent> events, LocalDate today, int scanned, List<String> failed) {
        StringBuilder sb = new StringBuilder();
        sb.append("Market: ").append(marketLine(index)).append('\n');
        String incomplete = IndexLines.incompleteWarning(failed.size(), scanned + failed.size());
        if (!incomplete.isEmpty()) {
            sb.append(incomplete).append('\n');
        }
        sb.append("Positional setups: weekly chart, hold ~1-6 months.\n");

        for (Section section : sections) {
            String indexLine = section.indexLine() != null ? section.indexLine() : section.segment().indexName();
            sb.append("\n%s %s · %s · %d scanned%n".formatted(section.segment().icon(),
                    section.segment().title().toUpperCase(Locale.ENGLISH), indexLine, section.scanned()));
            sb.append(section.bullish().isEmpty() ? "🟢 No strong long-term setups.\n" : "🟢 Setups\n");
            int n = 1;
            for (TradeSignal s : section.bullish()) {
                TradePlan p = s.plan();
                sb.append("%d. %s %d · %s · confidence %s%n".formatted(n++, s.symbol(), s.score(),
                        s.classification().label(), s.confidence()));
                sb.append(String.format(Locale.ENGLISH, "   Close %s · Stop %s (-%.1f%%) · Target %s%n",
                        rupees(p.entry()), rupees(p.stopLoss()), p.riskPct(), rupees(p.target())));
                sb.append("   Why: ").append(String.join("; ", s.bullishFactors().stream().limit(3).toList())).append('\n');
                if (!s.bearishFactors().isEmpty()) {
                    sb.append("   ⚠️ ").append(String.join("; ", s.bearishFactors())).append('\n');
                }
                MarketEvent results = resultsSoon.get(s.symbol());
                if (results != null) {
                    sb.append("   ⚠️ Results on %s (in %d days) - consider waiting for the numbers before entering%n"
                            .formatted(SHORT_DAY.format(results.date()), ChronoUnit.DAYS.between(today, results.date())));
                }
                sb.append("   ✋ ").append(s.invalidation()).append('\n');
            }
            if (!section.bearish().isEmpty()) {
                sb.append("🔴 Avoid / review long-term holdings\n");
                for (TradeSignal s : section.bearish()) {
                    sb.append("   %s %d · %s · %s%n".formatted(s.symbol(), s.score(), rupees(s.close()),
                            String.join("; ", s.bearishFactors().stream().limit(2).toList())));
                }
            }
        }

        if (!events.isEmpty()) {
            sb.append("\n📅 NEXT 7 DAYS\n");
            for (MarketEvent e : events) {
                sb.append("- %s · %s [%s]%n".formatted(EVENT_DAY.format(e.date()), e.title(), e.impact()));
            }
        }

        sb.append("\nScanned %d stocks".formatted(scanned));
        String skipped = IndexLines.skipped(failed);
        if (!skipped.isEmpty()) {
            sb.append(" · ").append(skipped);
        }
        sb.append("\n\nRule-based signals on weekly candles, not predictions. Review fundamentals and results "
                + "dates before investing. Not investment advice.");
        return sb.toString();
    }

    static String marketLine(PositionalSnapshot index) {
        return String.format(Locale.ENGLISH, "Nifty %,.2f (%+.1f%% over 6 months) · %s",
                index.close(), index.return6m() * 100, marketMood(index));
    }

    private static String marketMood(PositionalSnapshot nifty) {
        boolean above = nifty.close() > nifty.sma40w();
        boolean rising = nifty.sma40w() > nifty.sma40wFourWeeksAgo();
        if (above && rising) return "above a rising 40-week average → long-term uptrend";
        if (above) return "above a flattening 40-week average → trend losing steam";
        if (rising) return "below a still-rising 40-week average → correction within uptrend";
        return "below a falling 40-week average → long-term downtrend, be defensive";
    }
}
