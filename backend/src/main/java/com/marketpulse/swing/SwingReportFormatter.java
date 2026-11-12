package com.marketpulse.swing;

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

/** Plain-text swing watchlist, one section per market-cap segment, readable in Telegram and email. */
final class SwingReportFormatter {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
    private static final DateTimeFormatter EVENT_DAY = DateTimeFormatter.ofPattern("EEE dd MMM", Locale.ENGLISH);

    private SwingReportFormatter() {
    }

    static String subject(LocalDate session, int bullish, int bearish) {
        return "📊 Swing watchlist · %s · %d bullish, %d to avoid".formatted(DAY.format(session), bullish, bearish);
    }

    static String body(EodWatchlistService.MarketContext market, List<Section> sections,
                       Map<String, MarketEvent> resultsSoon, List<MarketEvent> events, LocalDate today,
                       int scanned, List<String> failed) {
        StringBuilder sb = new StringBuilder();
        sb.append("Market: ").append(marketLine(market)).append('\n');
        String incomplete = IndexLines.incompleteWarning(failed.size(), scanned + failed.size());
        if (!incomplete.isEmpty()) {
            sb.append(incomplete).append('\n');
        }
        sb.append("Swing setups hold ~3-20 days.\n");

        for (Section section : sections) {
            sb.append('\n').append(header(section)).append('\n');
            if (section.bullish().isEmpty()) {
                sb.append("🟢 No strong setups.\n");
            } else {
                sb.append("🟢 Setups\n");
            }
            int n = 1;
            for (TradeSignal s : section.bullish()) {
                appendSetup(sb, n++, s, resultsSoon.get(s.symbol()), today);
            }
            if (!section.bearish().isEmpty()) {
                sb.append("🔴 Avoid / review if holding\n");
                for (TradeSignal s : section.bearish()) {
                    sb.append("   %s %d · %s · %s%n".formatted(s.symbol(), s.score(), rupees(s.close()),
                            String.join("; ", s.bearishFactors().stream().limit(2).toList())));
                }
            }
        }

        if (!events.isEmpty()) {
            sb.append("\n📅 UPCOMING EVENTS (next 7 days)\n");
            for (MarketEvent e : events) {
                sb.append("- %s · %s [%s]%n".formatted(EVENT_DAY.format(e.date()), e.title(), e.impact()));
            }
        }

        sb.append("\nScanned %d stocks".formatted(scanned));
        String skipped = IndexLines.skipped(failed);
        if (!skipped.isEmpty()) {
            sb.append(" · ").append(skipped);
        }
        sb.append("\n\nRule-based signals, not predictions. Check upcoming results dates and news before buying. "
                + "Not investment advice.");
        return sb.toString();
    }

    static String header(Section s) {
        String index = s.indexLine() != null ? s.indexLine() : s.segment().indexName();
        return "%s %s · %s · %d scanned".formatted(s.segment().icon(), s.segment().title().toUpperCase(Locale.ENGLISH),
                index, s.scanned());
    }

    private static void appendSetup(StringBuilder sb, int n, TradeSignal s, MarketEvent results, LocalDate today) {
        TradePlan p = s.plan();
        sb.append("%d. %s %d · %s · confidence %s%n".formatted(n, s.symbol(), s.score(), s.classification().label(),
                s.confidence()));
        sb.append(String.format(Locale.ENGLISH, "   Close %s · Stop %s (-%.1f%%) · Target %s%n",
                rupees(p.entry()), rupees(p.stopLoss()), p.riskPct(), rupees(p.target())));
        sb.append("   Why: ").append(String.join("; ", s.bullishFactors().stream().limit(3).toList())).append('\n');
        if (!s.bearishFactors().isEmpty()) {
            sb.append("   ⚠️ ").append(String.join("; ", s.bearishFactors())).append('\n');
        }
        if (results != null) {
            sb.append("   ⚠️ Results on %s (in %d days) - a results surprise can break any setup%n".formatted(
                    SHORT_DAY.format(results.date()), ChronoUnit.DAYS.between(today, results.date())));
        }
        sb.append("   ✋ ").append(s.invalidation()).append('\n');
    }

    static String marketLine(EodWatchlistService.MarketContext market) {
        return String.format(Locale.ENGLISH, "Nifty %,.2f (%+.2f%% today) · %s",
                market.close(), market.dayChangePct(), marketMood(market));
    }

    private static String marketMood(EodWatchlistService.MarketContext m) {
        if (m.aboveEma50() && m.aboveEma200()) return "above 50 & 200-day EMA → supportive for longs";
        if (m.aboveEma200()) return "above 200-day but below 50-day EMA → be selective";
        return "below 200-day EMA → weak market, size positions smaller";
    }
}
