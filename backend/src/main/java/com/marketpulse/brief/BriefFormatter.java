package com.marketpulse.brief;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** The parts of the brief that are produced without the LLM. */
final class BriefFormatter {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);

    private BriefFormatter() {
    }

    static String subject(LocalDate date) {
        return "🌅 Pre-market brief · " + DAY.format(date);
    }

    /** Exact computed numbers, appended to every brief so the AI text can be checked against them. */
    static String cueTable(List<CueReading> cues, List<String> missing) {
        StringBuilder sb = new StringBuilder("📈 GLOBAL CUES (last close vs previous)\n");
        for (CueReading c : cues) {
            sb.append(String.format(Locale.ENGLISH, "%s %s: %,.2f (%+.2f%%)%n",
                    arrow(c.changePct()), c.name(), c.last(), c.changePct()));
        }
        if (!missing.isEmpty()) {
            sb.append("Unavailable: ").append(String.join(", ", missing)).append('\n');
        }
        return sb.toString();
    }

    static String cueLine(CueReading c) {
        return String.format(Locale.ENGLISH, "%s: %,.2f (%+.2f%%) as of %s",
                c.name(), c.last(), c.changePct(), SHORT_DAY.format(c.asOf()));
    }

    static String footer() {
        return "\nInformation only, not investment advice.";
    }

    private static String arrow(double changePct) {
        if (changePct > 0.05) return "▲";
        if (changePct < -0.05) return "▼";
        return "•";
    }
}
