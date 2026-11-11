package com.marketpulse.holdings;

import java.util.List;
import java.util.Locale;

import static com.marketpulse.signals.Money.rupees;

/** Plain-text holdings check for Telegram and email. */
final class HoldingsFormatter {

    private HoldingsFormatter() {
    }

    static String body(List<HoldingCheck> checks, List<String> failed) {
        StringBuilder sb = new StringBuilder();
        double totalPnl = checks.stream().mapToDouble(HoldingCheck::pnlAmount).sum();
        sb.append(String.format(Locale.ENGLISH, "Total P&L: %s%s%n", totalPnl >= 0 ? "+" : "-", rupees(Math.abs(totalPnl))));
        for (HoldingCheck c : checks) {
            Holding h = c.holding();
            sb.append("\n%s %s %s · %d @ %s%n".formatted(icon(c.verdict()), c.verdict(), h.symbol(), h.quantity(),
                    rupees(h.buyPrice())));
            sb.append(String.format(Locale.ENGLISH, "   Now %s · P&L %+.1f%% (%s%s) · Stop %s (%s)%n",
                    rupees(c.price()), c.pnlPct(), c.pnlAmount() >= 0 ? "+" : "-", rupees(Math.abs(c.pnlAmount())),
                    rupees(c.stop()), c.stopSource()));
            c.reasons().forEach(r -> sb.append("   - ").append(r).append('\n'));
            c.notes().forEach(n -> sb.append("   ℹ️ ").append(n).append('\n'));
        }
        if (!failed.isEmpty()) {
            sb.append("\nData unavailable: ").append(String.join(", ", failed)).append('\n');
        }
        sb.append("\nRule-based check, not advice. You decide whether to act.");
        return sb.toString();
    }

    private static String icon(Verdict v) {
        return switch (v) {
            case EXIT -> "🔴";
            case REVIEW -> "🟡";
            case HOLD -> "🟢";
        };
    }
}
