package com.marketpulse.tracking;

import com.marketpulse.signals.Classification;
import com.marketpulse.signals.Confidence;
import com.marketpulse.signals.TradeStyle;
import com.marketpulse.universe.Segment;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PerformanceCalculatorTest {

    private final PerformanceCalculator calculator = new PerformanceCalculator();

    @Test
    void summarisesClosedTradesPerStyle() {
        List<TrackedSignal> trades = List.of(
                closed(TradeStyle.SWING, 90, Confidence.HIGH, TradeStatus.TARGET_HIT, 10),
                closed(TradeStyle.SWING, 85, Confidence.HIGH, TradeStatus.STOPPED, -5),
                closed(TradeStyle.SWING, 70, Confidence.MEDIUM, TradeStatus.EXPIRED, 1),
                closed(TradeStyle.SWING, 68, Confidence.MEDIUM, TradeStatus.STOPPED, -4),
                open(TradeStyle.SWING),
                closed(TradeStyle.BTST, 95, Confidence.HIGH, TradeStatus.EXPIRED, 1.5));

        StyleStats swing = calculator.summarize(trades).stream()
                .filter(s -> s.style() == TradeStyle.SWING).findFirst().orElseThrow();

        assertThat(swing.open()).isEqualTo(1);
        assertThat(swing.closed()).isEqualTo(4);
        assertThat(swing.winRatePct()).isCloseTo(50.0, within(1e-9));
        assertThat(swing.avgReturnPct()).isCloseTo(0.5, within(1e-9));      // (10 - 5 + 1 - 4) / 4
        assertThat(swing.avgWinPct()).isCloseTo(5.5, within(1e-9));         // (10 + 1) / 2
        assertThat(swing.avgLossPct()).isCloseTo(-4.5, within(1e-9));       // (-5 - 4) / 2
        assertThat(swing.targetHits()).isEqualTo(1);
        assertThat(swing.stops()).isEqualTo(2);
        assertThat(swing.expired()).isEqualTo(1);
        assertThat(swing.bestPct()).isCloseTo(10, within(1e-9));
        assertThat(swing.worstPct()).isCloseTo(-5, within(1e-9));
    }

    @Test
    void splitsResultsByScoreBandAndConfidence() {
        List<TrackedSignal> trades = List.of(
                closed(TradeStyle.SWING, 90, Confidence.HIGH, TradeStatus.TARGET_HIT, 10),
                closed(TradeStyle.SWING, 85, Confidence.HIGH, TradeStatus.STOPPED, -5),
                closed(TradeStyle.SWING, 70, Confidence.MEDIUM, TradeStatus.STOPPED, -4));

        StyleStats swing = calculator.summarize(trades).getFirst();

        assertThat(swing.byScore()).extracting(Bucket::label, Bucket::closed)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("80-100", 2),
                        org.assertj.core.groups.Tuple.tuple("65-79", 1));
        assertThat(swing.byScore().getFirst().winRatePct()).isCloseTo(50.0, within(1e-9));
        assertThat(swing.byConfidence()).extracting(Bucket::label).containsExactly("HIGH", "MEDIUM");
    }

    @Test
    void comparesResultsByMarketCapSegment() {
        List<TrackedSignal> trades = List.of(
                closed(TradeStyle.SWING, 90, Confidence.HIGH, TradeStatus.TARGET_HIT, 10).withSegment(Segment.LARGE),
                closed(TradeStyle.SWING, 85, Confidence.HIGH, TradeStatus.STOPPED, -5).withSegment(Segment.SMALL),
                closed(TradeStyle.SWING, 80, Confidence.HIGH, TradeStatus.STOPPED, -4).withSegment(Segment.SMALL),
                closed(TradeStyle.SWING, 75, Confidence.HIGH, TradeStatus.EXPIRED, 1)); // recorded before segments

        StyleStats swing = calculator.summarize(trades).getFirst();

        assertThat(swing.bySegment()).extracting(Bucket::label, Bucket::closed)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Large cap", 1),
                        org.assertj.core.groups.Tuple.tuple("Small cap", 2));
        assertThat(swing.bySegment().get(1).avgReturnPct()).isCloseTo(-4.5, within(1e-9));
    }

    @Test
    void stylesWithNoTradesAreStillListedWithZeros() {
        List<StyleStats> stats = calculator.summarize(List.of());

        assertThat(stats).extracting(StyleStats::style)
                .containsExactly(TradeStyle.SWING, TradeStyle.POSITIONAL, TradeStyle.BTST);
        assertThat(stats.getFirst().closed()).isZero();
        assertThat(stats.getFirst().winRatePct()).isZero();
    }

    private static TrackedSignal closed(TradeStyle style, int score, Confidence confidence, TradeStatus status,
                                        double returnPct) {
        TrackedSignal t = TrackedSignal.open(style, "X" + score, LocalDate.of(2026, 11, 1), score,
                Classification.of(score), confidence, 100, 95, 110, Instant.EPOCH);
        return t.close(status, LocalDate.of(2026, 11, 5), 100 * (1 + returnPct / 100), 3, Math.max(returnPct, 0),
                Math.min(returnPct, 0));
    }

    private static TrackedSignal open(TradeStyle style) {
        return TrackedSignal.open(style, "OPEN", LocalDate.of(2026, 11, 10), 80, Classification.STRONG_BULLISH,
                Confidence.HIGH, 100, 95, 110, Instant.EPOCH);
    }
}
