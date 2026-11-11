package com.marketpulse.btst;

import com.marketpulse.alerts.AlertDispatcher;
import com.marketpulse.events.EventCalendar;
import com.marketpulse.events.Impact;
import com.marketpulse.events.MarketEvent;
import com.marketpulse.events.Source;
import com.marketpulse.market.Candle;
import com.marketpulse.market.IntradayCandle;
import com.marketpulse.market.IntradayDataClient;
import com.marketpulse.market.MarketDataClient;
import com.marketpulse.market.TestCandles;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BtstScanServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 11, 11);

    private final Map<String, List<Candle>> daily = new HashMap<>();
    private final Map<String, List<IntradayCandle>> intraday = new HashMap<>();
    private final MarketDataClient dailyClient = symbol -> {
        List<Candle> c = daily.get(symbol);
        if (c == null) throw new IllegalStateException("no data for " + symbol);
        return c;
    };
    private final IntradayDataClient intradayClient = symbol -> intraday.getOrDefault(symbol, List.of());
    private final List<String> subjects = new ArrayList<>();
    private final List<String> bodies = new ArrayList<>();
    private final AlertDispatcher dispatcher = new AlertDispatcher(List.of((s, b) -> { subjects.add(s); bodies.add(b); }));
    private EventCalendar calendar = new EventCalendar(List.of());
    private Clock clock = at(WEDNESDAY);

    @Test
    void listsStrongClosesAsCandidatesAndLeavesOutWeakOnes() {
        nifty(WEDNESDAY, 0.005);
        strong("RISER", WEDNESDAY);
        weak("FADER", WEDNESDAY);

        BtstRunSummary summary = service(List.of("RISER", "FADER")).run(false);

        assertThat(summary).isEqualTo(new BtstRunSummary(2, 0, 1, true, null));
        assertThat(subjects.getFirst()).contains("BTST").contains("11 Nov 2026").contains("1 candidate");
        assertThat(bodies.getFirst()).contains("RISER").doesNotContain("FADER")
                .contains("Stop").contains("Target").contains("Exit by");
    }

    @Test
    void skipsWhenThereIsNoSessionToday() {
        nifty(WEDNESDAY.minusDays(1), 0.005);

        BtstRunSummary summary = service(List.of("RISER")).run(false);

        assertThat(summary.sent()).isFalse();
        assertThat(summary.skippedReason()).contains("closed");
    }

    @Test
    void excludesStocksWithResultsBeforeTomorrowsSession() {
        nifty(WEDNESDAY, 0.005);
        strong("RISER", WEDNESDAY);
        calendar = new EventCalendar(List.of(MarketEvent.results(WEDNESDAY.plusDays(1), "RISER", Source.CONFIG)));

        BtstRunSummary summary = service(List.of("RISER")).run(false);

        assertThat(summary.candidates()).isZero();
        assertThat(bodies.getFirst()).contains("No BTST candidates").contains("Results due: RISER");
    }

    @Test
    void fridayLooksAcrossTheWeekendForResults() {
        LocalDate friday = LocalDate.of(2026, 11, 13);
        clock = at(friday);
        nifty(friday, 0.005);
        strong("RISER", friday);
        calendar = new EventCalendar(List.of(MarketEvent.results(friday.plusDays(3), "RISER", Source.CONFIG))); // Monday

        assertThat(service(List.of("RISER")).run(false).candidates()).isZero();
    }

    @Test
    void warnsAboutOvernightMacroEvents() {
        nifty(WEDNESDAY, 0.005);
        strong("RISER", WEDNESDAY);
        calendar = new EventCalendar(List.of(MarketEvent.macro(WEDNESDAY, "US Fed rate decision", Impact.HIGH)));

        service(List.of("RISER")).run(false);

        assertThat(bodies.getFirst()).contains("⚠️ Overnight event risk: US Fed rate decision");
    }

    @Test
    void failingSymbolDoesNotStopTheScan() {
        nifty(WEDNESDAY, 0.005);
        strong("RISER", WEDNESDAY);

        BtstRunSummary summary = service(List.of("GHOST", "RISER")).run(false);

        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.candidates()).isEqualTo(1);
    }

    @Test
    void sessionFractionProjectsTodaysVolume() {
        assertThat(BtstScanService.sessionFraction(java.time.LocalTime.of(15, 0))).isEqualTo(345.0 / 375);
        assertThat(BtstScanService.sessionFraction(java.time.LocalTime.of(12, 22, 30))).isEqualTo(187.0 / 375);
        assertThat(BtstScanService.sessionFraction(java.time.LocalTime.of(8, 30))).isEqualTo(1.0);  // not open yet
        assertThat(BtstScanService.sessionFraction(java.time.LocalTime.of(16, 0))).isEqualTo(1.0);  // after close
    }

    // ---------- helpers ----------

    private BtstScanService service(List<String> universe) {
        return new BtstScanService(dailyClient, intradayClient, new BtstCalculator(), new BtstScorer(), dispatcher,
                calendar, clock, new BtstSettings(universe, "^NSEI", 5));
    }

    private static Clock at(LocalDate day) {
        return Clock.fixed(day.atTime(15, 0).atZone(IST).toInstant(), IST);
    }

    private void nifty(LocalDate lastSession, double dayMove) {
        daily.put("^NSEI", withToday(TestCandles.trend(250, 20000, 0.0005, 0, lastSession.minusDays(1)), lastSession,
                dayMove, 0.9, 0));
    }

    /** Up ~3% today, trading near the high on heavy volume, rising into the close. */
    private void strong(String symbol, LocalDate today) {
        daily.put(symbol, withToday(TestCandles.trend(250, 100, 0.002, 1_000, today.minusDays(1)), today, 0.03, 0.95, 1_840));
        intraday.put(symbol, rising(today, true));
    }

    /** Down ~3% today, trading near the low, fading into the close. */
    private void weak(String symbol, LocalDate today) {
        daily.put(symbol, withToday(TestCandles.trend(250, 100, 0.002, 1_000, today.minusDays(1)), today, -0.03, 0.05, 1_840));
        intraday.put(symbol, rising(today, false));
    }

    private static List<Candle> withToday(List<Candle> history, LocalDate today, double move, double location, long volume) {
        List<Candle> candles = new ArrayList<>(history);
        double prev = history.getLast().close();
        double price = prev * (1 + move);
        double low = Math.min(prev, price) * 0.995;
        double high = Math.max(prev, price) * 1.005;
        // place the price at the requested location within the day's range
        double range = (price - low) / location;
        if (location < 1 && location > 0) {
            high = low + range;
        }
        candles.add(new Candle(today, prev, Math.max(high, price), low, price, volume));
        return candles;
    }

    private static List<IntradayCandle> rising(LocalDate day, boolean up) {
        List<IntradayCandle> bars = new ArrayList<>();
        Instant start = day.atTime(14, 0).atZone(IST).toInstant();
        for (int i = 0; i < 5; i++) {
            double close = up ? 100 + i : 100 - i;
            bars.add(new IntradayCandle(start.plusSeconds(900L * i), close, close, close, close, 100));
        }
        return bars;
    }
}
