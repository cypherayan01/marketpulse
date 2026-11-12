package com.marketpulse.predictions;

import com.marketpulse.signals.Classification;
import com.marketpulse.signals.TradeStyle;
import com.marketpulse.universe.Segment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
class JdbcPredictionRepositoryTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 11);

    @Autowired private JdbcClient jdbc;
    @Autowired private NamedParameterJdbcTemplate named;
    private JdbcPredictionRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JdbcPredictionRepository(jdbc, named);
    }

    @Test
    void savesAPredictionOnceAndReadsItBack() {
        Prediction p = live("HDFCBANK", DAY, Call.BUY);

        assertThat(repository.saveIfNew(p)).isTrue();
        assertThat(repository.saveIfNew(p)).isFalse(); // same scan run twice

        Prediction stored = repository.findAll(PredictionSource.LIVE).getFirst();
        assertThat(stored.id()).isNotNull();
        assertThat(stored.segment()).isEqualTo(Segment.LARGE);
        assertThat(stored.call()).isEqualTo(Call.BUY);
        assertThat(stored.newsSentiment()).isEqualTo(1);
        assertThat(stored.returns()).isEmpty();
    }

    @Test
    void storesOutcomesAsTheyArrive() {
        repository.saveIfNew(live("HDFCBANK", DAY, Call.BUY));
        Prediction pending = repository.findPending(PredictionSource.LIVE, DAY.minusDays(100)).getFirst();

        repository.update(pending.withOutcomes(Map.of(1, 1.5, 5, 3.0), Map.of(1, 0.2, 5, 1.0), 0.8, 2.1));

        Prediction stored = repository.findAll(PredictionSource.LIVE).getFirst();
        assertThat(stored.returns()).containsEntry(1, 1.5).containsEntry(5, 3.0).doesNotContainKey(10);
        assertThat(stored.niftyReturns()).containsEntry(5, 1.0);
        assertThat(stored.nextOpenPct()).isEqualTo(0.8);
        assertThat(stored.nextHighPct()).isEqualTo(2.1);
    }

    @Test
    void completePredictionsAreNoLongerPending() {
        Map<Integer, Double> all = Map.of(1, 1.0, 2, 1.0, 3, 1.0, 5, 1.0, 10, 1.0, 20, 1.0, 40, 1.0, 60, 1.0);
        repository.saveIfNew(live("DONE", DAY.minusDays(90), Call.BUY));
        repository.update(repository.findAll(PredictionSource.LIVE).getFirst().withOutcomes(all, all, 1.0, 1.0));
        repository.saveIfNew(live("WAITING", DAY, Call.BUY));

        assertThat(repository.findPending(PredictionSource.LIVE, DAY.minusDays(100)))
                .extracting(Prediction::symbol).containsExactly("WAITING");
    }

    @Test
    void listsPredictionsByDateAndTheDatesThatHaveThem() {
        repository.saveIfNew(live("A", DAY, Call.BUY));
        repository.saveIfNew(live("B", DAY, Call.AVOID));
        repository.saveIfNew(live("C", DAY.minusDays(1), Call.BUY));

        assertThat(repository.findByDate(PredictionSource.LIVE, DAY)).extracting(Prediction::symbol)
                .containsExactlyInAnyOrder("A", "B");
        assertThat(repository.findDates(PredictionSource.LIVE)).containsExactly(DAY, DAY.minusDays(1));
    }

    @Test
    void replaceReplayKeepsLivePredictionsUntouched() {
        repository.saveIfNew(live("LIVE1", DAY, Call.BUY));
        Prediction replay = Prediction.of(PredictionSource.REPLAY, TradeStyle.SWING, "OLD", Segment.MID,
                DAY.minusDays(30), 80, Classification.STRONG_BULLISH, Call.BUY, 50, 900, 0, Instant.EPOCH)
                .withOutcomes(Map.of(5, 2.0), Map.of(5, 1.0), null, null);

        repository.replaceReplay(List.of(replay, replay.withOutcomes(Map.of(), Map.of(), null, null)));
        repository.replaceReplay(List.of(replay)); // a second replay replaces the first

        assertThat(repository.findAll(PredictionSource.REPLAY)).hasSize(1);
        assertThat(repository.findAll(PredictionSource.REPLAY).getFirst().returns()).containsEntry(5, 2.0);
        assertThat(repository.findAll(PredictionSource.LIVE)).extracting(Prediction::symbol).containsExactly("LIVE1");
    }

    private static Prediction live(String symbol, LocalDate day, Call call) {
        return Prediction.of(PredictionSource.LIVE, TradeStyle.SWING, symbol, Segment.LARGE, day,
                call == Call.BUY ? 86 : 20, call == Call.BUY ? Classification.STRONG_BULLISH : Classification.STRONG_BEARISH,
                call, 806.6, 25255.05, 1, Instant.parse("2026-11-11T11:00:00Z"));
    }
}
