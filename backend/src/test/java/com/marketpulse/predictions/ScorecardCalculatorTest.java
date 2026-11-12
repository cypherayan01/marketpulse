package com.marketpulse.predictions;

import com.marketpulse.signals.Classification;
import com.marketpulse.signals.TradeStyle;
import com.marketpulse.universe.Segment;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ScorecardCalculatorTest {

    private final ScorecardCalculator calculator = new ScorecardCalculator();

    @Test
    void reportsHitRateAndAverageEdgeAtEachCheckpoint() {
        List<Prediction> predictions = List.of(
                swing(Call.BUY, 90, Segment.LARGE, 1, 5, 4.0, 1.0),   // edge +3: right
                swing(Call.BUY, 70, Segment.SMALL, 0, 5, 0.0, 1.0),   // edge -1: wrong
                swing(Call.AVOID, 20, Segment.LARGE, -1, 5, -2.0, 1.0)); // edge +3: right

        StyleScorecard swing = calculator.summarize(predictions).stream()
                .filter(s -> s.style() == TradeStyle.SWING).findFirst().orElseThrow();
        Checkpoint fiveDays = swing.checkpoints().stream().filter(c -> c.sessions() == 5).findFirst().orElseThrow();

        assertThat(swing.predictions()).isEqualTo(3);
        assertThat(fiveDays.measured()).isEqualTo(3);
        assertThat(fiveDays.hitRatePct()).isCloseTo(66.67, within(0.01));
        assertThat(fiveDays.avgEdgePct()).isCloseTo(5.0 / 3, within(1e-9));
        assertThat(swing.checkpoints().stream().filter(c -> c.sessions() == 10).findFirst().orElseThrow().measured())
                .isZero();
    }

    @Test
    void splitsTheKeyCheckpointByScoreSegmentCallAndNewsAgreement() {
        List<Prediction> predictions = List.of(
                swing(Call.BUY, 90, Segment.LARGE, 1, 5, 4.0, 1.0),
                swing(Call.BUY, 70, Segment.SMALL, -1, 5, 0.0, 1.0),
                swing(Call.AVOID, 20, Segment.LARGE, 0, 5, -2.0, 1.0));

        StyleScorecard swing = calculator.summarize(predictions).getFirst();

        assertThat(swing.keySessions()).isEqualTo(5);
        assertThat(swing.byCall()).extracting(Breakdown::label).containsExactly("Buy", "Avoid");
        assertThat(swing.byScore()).extracting(Breakdown::label, Breakdown::measured)
                .contains(org.assertj.core.groups.Tuple.tuple("Buy 80+", 1), org.assertj.core.groups.Tuple.tuple("Buy 65-79", 1));
        assertThat(swing.bySegment()).extracting(Breakdown::label).containsExactly("Large cap", "Small cap");
        assertThat(swing.byNews()).extracting(Breakdown::label, Breakdown::measured).contains(
                org.assertj.core.groups.Tuple.tuple("News agreed", 1),
                org.assertj.core.groups.Tuple.tuple("News disagreed", 1),
                org.assertj.core.groups.Tuple.tuple("No news", 1));
    }

    @Test
    void btstIsJudgedOnTheNextMorning() {
        Prediction btst = Prediction.of(PredictionSource.LIVE, TradeStyle.BTST, "TRENT", Segment.LARGE,
                LocalDate.of(2026, 11, 11), 92, Classification.STRONG_BULLISH, Call.BUY, 100, 1000, 0, Instant.EPOCH)
                .withOutcomes(Map.of(1, 2.0), Map.of(1, 0.5), 1.2, 2.5);

        StyleScorecard card = calculator.summarize(List.of(btst)).stream()
                .filter(s -> s.style() == TradeStyle.BTST).findFirst().orElseThrow();

        assertThat(card.nextMorning().measured()).isEqualTo(1);
        assertThat(card.nextMorning().gapUpPct()).isCloseTo(100.0, within(1e-9));
        assertThat(card.nextMorning().avgOpenPct()).isCloseTo(1.2, within(1e-9));
        assertThat(card.nextMorning().avgHighPct()).isCloseTo(2.5, within(1e-9));
    }

    private static Prediction swing(Call call, int score, Segment segment, int news, int sessions, double stock, double nifty) {
        return Prediction.of(PredictionSource.LIVE, TradeStyle.SWING, "S" + score, segment, LocalDate.of(2026, 11, 2),
                score, Classification.of(score), call, 100, 1000, news, Instant.EPOCH)
                .withOutcomes(Map.of(sessions, stock), Map.of(sessions, nifty), null, null);
    }
}
