package com.marketpulse.news;

import com.marketpulse.alerts.AlertDispatcher;
import com.marketpulse.alerts.Notifier;
import com.marketpulse.events.EventCalendar;
import com.marketpulse.reports.NewsEntry;
import com.marketpulse.reports.ReportStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewsAlertServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T05:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final NewsRelevanceFilter filter = new NewsRelevanceFilter(List.of("rbi", "crude", "fed"), List.of());
    private final NewsAlertSettings settings = new NewsAlertSettings(Duration.ofMinutes(30), 7, 20);

    private FakeAnalyzer analyzer;
    private RecordingNotifier notifier;
    private SeenNewsRegistry seen;
    private EventCalendar calendar;
    private ReportStore store;

    @BeforeEach
    void setUp() {
        analyzer = new FakeAnalyzer();
        notifier = new RecordingNotifier();
        seen = new SeenNewsRegistry(1000);
        calendar = new EventCalendar(List.of());
        store = new ReportStore(100);
    }

    @Test
    void alertsOnlyForRelevantFreshHighImpactNews() {
        NewsSource source = () -> List.of(
                news("1", "RBI cuts repo rate unexpectedly", minutesAgo(5)),   // relevant, high impact
                news("2", "Crude oil inches up 0.2%", minutesAgo(10)),         // relevant, low impact
                news("3", "Movie review: weekend release", minutesAgo(5)));    // irrelevant
        analyzer.score("RBI cuts repo rate unexpectedly", 9);
        analyzer.score("Crude oil inches up 0.2%", 3);

        NewsRunSummary summary = service(source).run();

        assertThat(notifier.subjects).hasSize(1);
        assertThat(notifier.subjects.getFirst()).contains("RBI cuts repo rate unexpectedly");
        assertThat(analyzer.analyzedTitles).containsExactlyInAnyOrder(
                "RBI cuts repo rate unexpectedly", "Crude oil inches up 0.2%");
        assertThat(summary).isEqualTo(new NewsRunSummary(3, 3, 2, 1));
    }

    @Test
    void savesEveryScoredHeadlineForTheDashboard() {
        NewsSource source = () -> List.of(
                news("1", "RBI cuts repo rate unexpectedly", minutesAgo(5)),
                news("2", "Crude oil inches up 0.2%", minutesAgo(10)));
        analyzer.score("RBI cuts repo rate unexpectedly", 9);
        analyzer.score("Crude oil inches up 0.2%", 3);

        service(source).run();

        assertThat(store.recentNews()).extracting(NewsEntry::title, NewsEntry::alerted)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("RBI cuts repo rate unexpectedly", true),
                        org.assertj.core.groups.Tuple.tuple("Crude oil inches up 0.2%", false));
    }

    @Test
    void ignoresNewsOlderThanLookbackWindow() {
        NewsSource source = () -> List.of(news("1", "RBI policy outcome", minutesAgo(45)));
        analyzer.score("RBI policy outcome", 9);

        NewsRunSummary summary = service(source).run();

        assertThat(analyzer.analyzedTitles).isEmpty();
        assertThat(notifier.subjects).isEmpty();
        assertThat(summary.fresh()).isZero();
    }

    @Test
    void doesNotCallAnalyzerWhenNothingIsRelevant() {
        NewsSource source = () -> List.of(news("1", "Cricket team wins series", minutesAgo(2)));

        service(source).run();

        assertThat(analyzer.calls).isZero();
    }

    @Test
    void neverAlertsTheSameNewsTwice() {
        NewsSource source = () -> List.of(news("1", "Fed signals rate cut", minutesAgo(5)));
        analyzer.score("Fed signals rate cut", 8);
        NewsAlertService service = service(source);

        service.run();
        service.run();

        assertThat(notifier.subjects).hasSize(1);
        assertThat(analyzer.calls).isEqualTo(1);
    }

    @Test
    void deduplicatesSameStoryFromTwoFeedsInOneRun() {
        NewsSource a = () -> List.of(news("same", "RBI surprise", minutesAgo(5)));
        NewsSource b = () -> List.of(news("same", "RBI surprise", minutesAgo(5)));
        analyzer.score("RBI surprise", 9);

        service(a, b).run();

        assertThat(notifier.subjects).hasSize(1);
    }

    @Test
    void failingSourceDoesNotStopOtherSources() {
        NewsSource broken = () -> { throw new IllegalStateException("feed down"); };
        NewsSource ok = () -> List.of(news("1", "Crude spikes 6% on supply shock", minutesAgo(5)));
        analyzer.score("Crude spikes 6% on supply shock", 8);

        service(broken, ok).run();

        assertThat(notifier.subjects).hasSize(1);
    }

    @Test
    void analyzerFailureLeavesNewsUnseenSoNextRunRetries() {
        NewsSource source = () -> List.of(news("1", "RBI emergency meeting", minutesAgo(5)));
        analyzer.score("RBI emergency meeting", 9);
        analyzer.failNextCall = true;
        NewsAlertService service = service(source);

        service.run();
        assertThat(notifier.subjects).isEmpty();

        service.run();
        assertThat(notifier.subjects).hasSize(1);
    }

    @Test
    void sendsHeadlinesToAnalyzerInBatches() {
        NewsAlertSettings smallBatches = new NewsAlertSettings(Duration.ofMinutes(30), 7, 2);
        NewsSource source = () -> List.of(
                news("1", "RBI one", minutesAgo(1)),
                news("2", "RBI two", minutesAgo(1)),
                news("3", "RBI three", minutesAgo(1)));

        new NewsAlertService(List.of(source), filter, seen, analyzer,
                new AlertDispatcher(List.of(notifier)), calendar, clock, smallBatches, store).run();

        assertThat(analyzer.calls).isEqualTo(2);
        assertThat(analyzer.analyzedTitles).hasSize(3);
    }

    @Test
    void recordsAnnouncedResultsDatesInCalendarEvenWithoutAlert() {
        NewsSource source = () -> List.of(news("1", "Crude: Infosys board to consider Q3 results on Jan 15", minutesAgo(5)));
        analyzer.score("Crude: Infosys board to consider Q3 results on Jan 15", 3); // below alert threshold
        analyzer.results("Crude: Infosys board to consider Q3 results on Jan 15",
                new ResultsAnnouncement("INFY", LocalDate.of(2027, 1, 15)));

        service(source).run();

        assertThat(notifier.subjects).isEmpty();
        assertThat(calendar.nextResults("INFY", LocalDate.of(2027, 1, 1), 30)).isPresent();
    }

    // ---------- helpers & fakes ----------

    private NewsAlertService service(NewsSource... sources) {
        return new NewsAlertService(List.of(sources), filter, seen, analyzer,
                new AlertDispatcher(List.of(notifier)), calendar, clock, settings, store);
    }

    private static Instant minutesAgo(int minutes) {
        return NOW.minus(Duration.ofMinutes(minutes));
    }

    private static NewsItem news(String id, String title, Instant publishedAt) {
        return new NewsItem(id, title, "", "https://news/" + id, "Test", publishedAt);
    }

    private static class FakeAnalyzer implements NewsImpactAnalyzer {
        private final java.util.Map<String, Integer> scores = new java.util.HashMap<>();
        private final java.util.Map<String, ResultsAnnouncement> results = new java.util.HashMap<>();
        final List<String> analyzedTitles = new ArrayList<>();
        int calls;
        boolean failNextCall;

        void score(String title, int impact) {
            scores.put(title, impact);
        }

        void results(String title, ResultsAnnouncement announcement) {
            results.put(title, announcement);
        }

        @Override
        public List<NewsImpact> analyze(List<NewsItem> items) {
            calls++;
            if (failNextCall) {
                failNextCall = false;
                throw new IllegalStateException("Claude unavailable");
            }
            items.forEach(i -> analyzedTitles.add(i.title()));
            return items.stream()
                    .map(i -> new NewsImpact(i, scores.getOrDefault(i.title(), 1), Direction.BULLISH,
                            List.of("Banking"), List.of("HDFCBANK"), "test reason", results.get(i.title())))
                    .toList();
        }
    }

    private static class RecordingNotifier implements Notifier {
        final List<String> subjects = new ArrayList<>();

        @Override
        public void send(String subject, String body) {
            subjects.add(subject);
        }
    }
}
