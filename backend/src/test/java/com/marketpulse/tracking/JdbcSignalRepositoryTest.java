package com.marketpulse.tracking;

import com.marketpulse.signals.Classification;
import com.marketpulse.signals.Confidence;
import com.marketpulse.signals.TradeStyle;
import com.marketpulse.universe.Segment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@JdbcTest
class JdbcSignalRepositoryTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 11);

    @Autowired
    private JdbcClient jdbc;

    private JdbcSignalRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JdbcSignalRepository(jdbc);
    }

    @Test
    void savesAndReadsBackAnOpenTrade() {
        assertThat(repository.saveIfNew(trade(TradeStyle.SWING, "HDFCBANK", DAY))).isTrue();

        TrackedSignal saved = repository.findOpen().getFirst();
        assertThat(saved.id()).isNotNull();
        assertThat(saved.symbol()).isEqualTo("HDFCBANK");
        assertThat(saved.signalDate()).isEqualTo(DAY);
        assertThat(saved.classification()).isEqualTo(Classification.STRONG_BULLISH);
        assertThat(saved.entry()).isCloseTo(806.6, within(1e-9));
        assertThat(saved.createdAt()).isEqualTo(Instant.parse("2026-11-11T11:00:00Z"));
    }

    @Test
    void keepsTheMarketCapSegment() {
        repository.saveIfNew(trade(TradeStyle.SWING, "AARTIIND", DAY).withSegment(Segment.SMALL));
        repository.saveIfNew(trade(TradeStyle.SWING, "OLDROW", DAY)); // trades recorded before segments existed

        assertThat(repository.findAll()).extracting(TrackedSignal::symbol, TrackedSignal::segment)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("AARTIIND", Segment.SMALL),
                        org.assertj.core.groups.Tuple.tuple("OLDROW", null));
    }

    @Test
    void doesNotOpenASecondTradeInTheSameStockAndStyle() {
        repository.saveIfNew(trade(TradeStyle.SWING, "HDFCBANK", DAY));

        assertThat(repository.saveIfNew(trade(TradeStyle.SWING, "HDFCBANK", DAY.plusDays(1)))).isFalse();
        assertThat(repository.saveIfNew(trade(TradeStyle.POSITIONAL, "HDFCBANK", DAY.plusDays(1)))).isTrue();
    }

    @Test
    void sameSignalReportedTwiceOnTheSameDayIsStoredOnce() {
        TrackedSignal t = trade(TradeStyle.BTST, "TRENT", DAY);
        repository.saveIfNew(t);
        TrackedSignal stored = repository.findOpen().getFirst();
        repository.update(stored.close(TradeStatus.EXPIRED, DAY.plusDays(1), 810, 1, 0.4, 0));

        assertThat(repository.saveIfNew(t)).isFalse(); // closed already, but same day's signal again
    }

    @Test
    void updatesATradeWhenItCloses() {
        repository.saveIfNew(trade(TradeStyle.SWING, "HDFCBANK", DAY));
        TrackedSignal open = repository.findOpen().getFirst();

        repository.update(open.close(TradeStatus.TARGET_HIT, DAY.plusDays(4), 876.86, 4, 8.7, -1.2));

        assertThat(repository.findOpen()).isEmpty();
        TrackedSignal closed = repository.findAll().getFirst();
        assertThat(closed.status()).isEqualTo(TradeStatus.TARGET_HIT);
        assertThat(closed.exitDate()).isEqualTo(DAY.plusDays(4));
        assertThat(closed.returnPct()).isCloseTo((876.86 / 806.6 - 1) * 100, within(1e-9));
        assertThat(closed.sessionsHeld()).isEqualTo(4);
        assertThat(closed.maxLossPct()).isCloseTo(-1.2, within(1e-9));
    }

    @Test
    void listsNewestSignalsFirst() {
        repository.saveIfNew(trade(TradeStyle.SWING, "OLD", DAY.minusDays(5)));
        repository.saveIfNew(trade(TradeStyle.SWING, "NEW", DAY));

        assertThat(repository.findAll()).extracting(TrackedSignal::symbol).containsExactly("NEW", "OLD");
    }

    private static TrackedSignal trade(TradeStyle style, String symbol, LocalDate day) {
        return TrackedSignal.open(style, symbol, day, 86, Classification.STRONG_BULLISH, Confidence.HIGH,
                806.6, 771.47, 876.86, Instant.parse("2026-11-11T11:00:00Z"));
    }
}
