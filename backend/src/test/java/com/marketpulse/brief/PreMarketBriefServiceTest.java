package com.marketpulse.brief;

import com.marketpulse.alerts.AlertDispatcher;
import com.marketpulse.events.EventCalendar;
import com.marketpulse.events.Impact;
import com.marketpulse.events.MarketEvent;
import com.marketpulse.market.Candle;
import com.marketpulse.market.MarketDataClient;
import com.marketpulse.news.NewsItem;
import com.marketpulse.news.NewsRelevanceFilter;
import com.marketpulse.news.NewsSource;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PreMarketBriefServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 11, 11);
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);

    private final Map<String, List<Candle>> data = new HashMap<>();
    private final MarketDataClient market = symbol -> {
        List<Candle> c = data.get(symbol);
        if (c == null) throw new IllegalStateException("no data for " + symbol);
        return c;
    };
    private final List<CueDefinition> cues = List.of(
            new CueDefinition("S&P 500", "^GSPC"), new CueDefinition("Brent crude", "BZ=F"));
    private final NewsRelevanceFilter filter = new NewsRelevanceFilter(List.of("fed", "crude", "rbi"), List.of());
    private final List<NewsItem> headlines = new ArrayList<>();
    private final NewsSource source = () -> headlines;
    private final List<String> subjects = new ArrayList<>();
    private final List<String> bodies = new ArrayList<>();
    private final AlertDispatcher dispatcher = new AlertDispatcher(List.of((s, b) -> { subjects.add(s); bodies.add(b); }));
    private EventCalendar calendar = new EventCalendar(List.of());
    private Set<LocalDate> holidays = Set.of();
    private BriefInput captured;
    private BriefWriter writer = input -> { captured = input; return "AI: markets look calm."; };

    @Test
    void writesBriefFromComputedCuesAndAppendsTheNumbers() {
        data.put("^GSPC", closes(100, 101));
        data.put("BZ=F", closes(80, 78));

        BriefRunSummary summary = service(at(WEDNESDAY, 8, 30)).run(false);

        assertThat(summary.sent()).isTrue();
        assertThat(summary.aiWritten()).isTrue();
        CueReading sp = captured.cues().getFirst();
        assertThat(sp.name()).isEqualTo("S&P 500");
        assertThat(sp.changePct()).isCloseTo(1.0, within(1e-9));
        assertThat(captured.cues().get(1).changePct()).isCloseTo(-2.5, within(1e-9));
        assertThat(subjects.getFirst()).contains("Pre-market brief").contains("11 Nov 2026");
        assertThat(bodies.getFirst()).startsWith("AI: markets look calm.")
                .contains("S&P 500").contains("+1.00%").contains("-2.50%");
    }

    @Test
    void skipsWeekends() {
        BriefRunSummary summary = service(at(LocalDate.of(2026, 11, 14), 8, 30)).run(false);

        assertThat(summary.sent()).isFalse();
        assertThat(summary.skippedReason()).contains("weekend");
    }

    @Test
    void skipsConfiguredHolidays() {
        holidays = Set.of(WEDNESDAY);

        BriefRunSummary summary = service(at(WEDNESDAY, 8, 30)).run(false);

        assertThat(summary.skippedReason()).contains("holiday");
    }

    @Test
    void forceRunsOnWeekends() {
        data.put("^GSPC", closes(100, 101));
        data.put("BZ=F", closes(80, 78));

        assertThat(service(at(LocalDate.of(2026, 11, 14), 8, 30)).run(true).sent()).isTrue();
    }

    @Test
    void usesOnlyRelevantHeadlinesSincePreviousSessionClose() {
        data.put("^GSPC", closes(100, 101));
        data.put("BZ=F", closes(80, 78));
        headlines.add(news("before-close", "Fed minutes released", LocalDateTime.of(2026, 11, 13, 14, 0)));
        headlines.add(news("after-close", "Fed signals pause", LocalDateTime.of(2026, 11, 13, 16, 0)));
        headlines.add(news("weekend", "Crude jumps on supply cut", LocalDateTime.of(2026, 11, 15, 10, 0)));
        headlines.add(news("noise", "Celebrity wedding photos", LocalDateTime.of(2026, 11, 15, 11, 0)));

        service(at(MONDAY, 8, 30)).run(false); // previous session: Friday 13 Nov, closed 15:30 IST

        assertThat(captured.headlines()).extracting(NewsItem::id).containsExactly("weekend", "after-close");
    }

    @Test
    void fallsBackToNumbersOnlyWhenWriterFails() {
        data.put("^GSPC", closes(100, 101));
        data.put("BZ=F", closes(80, 78));
        writer = input -> { throw new IllegalStateException("Claude down"); };

        BriefRunSummary summary = service(at(WEDNESDAY, 8, 30)).run(false);

        assertThat(summary.sent()).isTrue();
        assertThat(summary.aiWritten()).isFalse();
        assertThat(bodies.getFirst()).contains("AI summary unavailable").contains("S&P 500").contains("+1.00%");
    }

    @Test
    void missingCueIsReportedButNotFatal() {
        data.put("^GSPC", closes(100, 101)); // no Brent data

        BriefRunSummary summary = service(at(WEDNESDAY, 8, 30)).run(false);

        assertThat(summary.cues()).isEqualTo(1);
        assertThat(captured.missingCues()).containsExactly("Brent crude");
    }

    @Test
    void passesEventsForTheNextFewDays() {
        data.put("^GSPC", closes(100, 101));
        data.put("BZ=F", closes(80, 78));
        calendar = new EventCalendar(List.of(
                MarketEvent.macro(WEDNESDAY, "US CPI inflation data", Impact.HIGH),
                MarketEvent.macro(WEDNESDAY.plusDays(10), "Later event", Impact.HIGH)));

        service(at(WEDNESDAY, 8, 30)).run(false);

        assertThat(captured.events()).extracting(MarketEvent::title).containsExactly("US CPI inflation data");
    }

    // ---------- helpers ----------

    private PreMarketBriefService service(Clock clock) {
        return new PreMarketBriefService(market, cues, List.of(source), filter, calendar, writer, dispatcher, clock,
                new BriefSettings(holidays, 40, 3));
    }

    private static Clock at(LocalDate date, int hour, int minute) {
        return Clock.fixed(date.atTime(hour, minute).atZone(IST).toInstant(), IST);
    }

    private static List<Candle> closes(double previous, double last) {
        return List.of(new Candle(WEDNESDAY.minusDays(2), previous, previous, previous, previous, 0),
                new Candle(WEDNESDAY.minusDays(1), last, last, last, last, 0));
    }

    private static NewsItem news(String id, String title, LocalDateTime istTime) {
        Instant at = istTime.atZone(IST).toInstant();
        return new NewsItem(id, title, "", "https://news/" + id, "Test", at);
    }
}
