package com.marketpulse.positional;

import com.marketpulse.events.MarketEvent;
import com.marketpulse.signals.TradePlan;
import com.marketpulse.signals.TradeSignal;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.marketpulse.signals.Money.rupees;

/** Plain-text weekly positional outlook for Telegram and email. */
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

    static String body(PositionalSnapshot index, List<TradeSignal> bullish, List<TradeSignal> bearish,
                       Map<String, MarketEvent> resultsSoon, List<MarketEvent> events, LocalDate today,
                       int scanned, List<String> failed) {
        StringBuilder sb = new StringBuilder();
        sb.append("Market: ").append(marketLine(index)).append("\n\n");

        sb.append("🟢 POSITIONAL SETUPS (weekly chart, hold ~1-6 months)\n");
        if (bullish.isEmpty()) {
            sb.append("No strong long-term setups this week.\n");
        }
        int n = 1;
        for (TradeSignal s : bullish) {
            TradePlan p = s.plan();
            sb.append("\n%d. %s - %d/100 %s · confidence %s%n".formatted(n++, s.symbol(), s.score(),
                    s.classification().label(), s.confidence()));
            sb.append(String.format(Locale.ENGLISH, "   Close %s | Stop %s (-%.1f%%) | Target %s%n",
                    rupees(p.entry()), rupees(p.stopLoss()), p.riskPct(), rupees(p.target())));
            sb.append("   Why: ").append(String.join("; ", s.bullishFactors())).append('\n');
            if (!s.bearishFactors().isEmpty()) {
                sb.append("   ⚠️ Watch: ").append(String.join("; ", s.bearishFactors())).append('\n');
            }
            MarketEvent results = resultsSoon.get(s.symbol());
            if (results != null) {
                sb.append("   ⚠️ Results on %s (in %d days) - consider waiting for the numbers before entering%n"
                        .formatted(SHORT_DAY.format(results.date()), ChronoUnit.DAYS.between(today, results.date())));
            }
            sb.append("   Invalidated if: ").append(s.invalidation()).append('\n');
        }

        sb.append("\n🔴 AVOID / REVIEW LONG-TERM HOLDINGS\n");
        if (bearish.isEmpty()) {
            sb.append("Nothing flagged.\n");
        }
        n = 1;
        for (TradeSignal s : bearish) {
            sb.append("\n%d. %s - %d/100 %s · close %s%n".formatted(n++, s.symbol(), s.score(),
                    s.classification().label(), rupees(s.close())));
            sb.append("   Why: ").append(String.join("; ", s.bearishFactors().stream().limit(4).toList())).append('\n');
        }

        if (!events.isEmpty()) {
            sb.append("\n📅 NEXT 7 DAYS\n");
            for (MarketEvent e : events) {
                sb.append("- %s · %s [%s]%n".formatted(EVENT_DAY.format(e.date()), e.title(), e.impact()));
            }
        }

        sb.append("\nScanned %d stocks".formatted(scanned));
        if (!failed.isEmpty()) {
            sb.append(" · Data unavailable: ").append(String.join(", ", failed));
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
