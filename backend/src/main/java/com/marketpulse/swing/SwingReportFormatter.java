package com.marketpulse.swing;

import com.marketpulse.events.MarketEvent;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.marketpulse.swing.SwingScorer.rupees;

/** Plain-text swing watchlist that reads well in both Telegram and email. */
final class SwingReportFormatter {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
    private static final DateTimeFormatter EVENT_DAY = DateTimeFormatter.ofPattern("EEE dd MMM", Locale.ENGLISH);

    private SwingReportFormatter() {
    }

    static String subject(LocalDate session, int bullish, int bearish) {
        return "📊 Swing watchlist · %s · %d bullish, %d to avoid".formatted(DAY.format(session), bullish, bearish);
    }

    static String body(EodWatchlistService.MarketContext market, List<SwingSignal> bullish, List<SwingSignal> bearish,
                       Map<String, MarketEvent> resultsSoon, List<MarketEvent> events, LocalDate today,
                       int scanned, List<String> failed) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ENGLISH, "Market: Nifty %,.2f (%+.2f%% today) · %s%n%n", market.close(), market.dayChangePct(), marketMood(market)));

        sb.append("🟢 BULLISH SETUPS (swing, hold ~3-20 days)\n");
        if (bullish.isEmpty()) {
            sb.append("No strong setups today - sitting out is a valid position.\n");
        }
        int n = 1;
        for (SwingSignal s : bullish) {
            TradePlan p = s.plan();
            sb.append("\n%d. %s - %d/100 %s · confidence %s%n".formatted(n++, s.symbol(), s.score(),
                    s.classification().label(), s.confidence()));
            sb.append(String.format(Locale.ENGLISH, "   Close %s | Stop %s (-%.1f%%) | Target %s%n", rupees(p.entry()), rupees(p.stopLoss()), p.riskPct(), rupees(p.target())));
            sb.append("   Why: ").append(String.join("; ", s.bullishFactors())).append('\n');
            if (!s.bearishFactors().isEmpty()) {
                sb.append("   ⚠️ Watch: ").append(String.join("; ", s.bearishFactors())).append('\n');
            }
            MarketEvent results = resultsSoon.get(s.symbol());
            if (results != null) {
                sb.append("   ⚠️ Results on %s (in %d days) - a results surprise can break any setup%n".formatted(
                        SHORT_DAY.format(results.date()), ChronoUnit.DAYS.between(today, results.date())));
            }
            sb.append("   Invalidated if: ").append(s.invalidation()).append('\n');
        }

        sb.append("\n🔴 AVOID / REVIEW IF HOLDING\n");
        if (bearish.isEmpty()) {
            sb.append("Nothing flagged.\n");
        }
        n = 1;
        for (SwingSignal s : bearish) {
            sb.append("\n%d. %s - %d/100 %s · close %s%n".formatted(n++, s.symbol(), s.score(),
                    s.classification().label(), rupees(s.snapshot().close())));
            sb.append("   Why: ").append(String.join("; ", s.bearishFactors().stream().limit(4).toList())).append('\n');
        }

        if (!events.isEmpty()) {
            sb.append("\n📅 UPCOMING EVENTS (next 7 days)\n");
            for (MarketEvent e : events) {
                sb.append("- %s · %s [%s]%n".formatted(EVENT_DAY.format(e.date()), e.title(), e.impact()));
            }
        }

        sb.append("\nScanned %d stocks".formatted(scanned));
        if (!failed.isEmpty()) {
            sb.append(" · Data unavailable: ").append(String.join(", ", failed));
        }
        sb.append("\n\nRule-based signals, not predictions. Check upcoming results dates and news before buying. "
                + "Not investment advice.");
        return sb.toString();
    }

    private static String marketMood(EodWatchlistService.MarketContext m) {
        if (m.aboveEma50() && m.aboveEma200()) return "above 50 & 200-day EMA → supportive for longs";
        if (m.aboveEma200()) return "above 200-day but below 50-day EMA → be selective";
        return "below 200-day EMA → weak market, size positions smaller";
    }
}
