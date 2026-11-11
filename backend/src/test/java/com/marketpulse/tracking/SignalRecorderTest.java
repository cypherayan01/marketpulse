package com.marketpulse.tracking;

import com.marketpulse.reports.ScanReport;
import com.marketpulse.signals.Classification;
import com.marketpulse.signals.Confidence;
import com.marketpulse.signals.TradePlan;
import com.marketpulse.signals.TradeSignal;
import com.marketpulse.signals.TradeStyle;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SignalRecorderTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 11);
    private final InMemorySignalRepository repository = new InMemorySignalRepository();
    private final SignalRecorder recorder = new SignalRecorder(repository);

    @Test
    void opensAPaperTradeForEveryBullishSetup() {
        recorder.accept(report(List.of(bullish("HDFCBANK", 86), bullish("AXISBANK", 85)), List.of(bearish("NTPC"))));

        assertThat(repository.rows).extracting(TrackedSignal::symbol).containsExactly("HDFCBANK", "AXISBANK");
        TrackedSignal hdfc = repository.rows.getFirst();
        assertThat(hdfc.style()).isEqualTo(TradeStyle.SWING);
        assertThat(hdfc.signalDate()).isEqualTo(DAY);
        assertThat(hdfc.entry()).isEqualTo(806.6);
        assertThat(hdfc.stop()).isEqualTo(771.47);
        assertThat(hdfc.target()).isEqualTo(876.86);
        assertThat(hdfc.status()).isEqualTo(TradeStatus.OPEN);
    }

    @Test
    void runningTheSameScanTwiceDoesNotDoubleCount() {
        ScanReport report = report(List.of(bullish("HDFCBANK", 86)), List.of());

        recorder.accept(report);
        recorder.accept(report);

        assertThat(repository.rows).hasSize(1);
    }

    private static ScanReport report(List<TradeSignal> bullish, List<TradeSignal> bearish) {
        return new ScanReport(TradeStyle.SWING, Instant.parse("2026-11-11T11:00:00Z"), DAY, "Nifty", bullish, bearish,
                Map.of(), List.of(), 50, List.of(), List.of());
    }

    private static TradeSignal bullish(String symbol, int score) {
        return new TradeSignal(symbol, TradeStyle.SWING, score, Classification.STRONG_BULLISH, Confidence.HIGH,
                List.of(), List.of(), new TradePlan(806.6, 771.47, 876.86, 4.36), "invalid", 806.6);
    }

    private static TradeSignal bearish(String symbol) {
        return new TradeSignal(symbol, TradeStyle.SWING, 20, Classification.STRONG_BEARISH, Confidence.HIGH,
                List.of(), List.of(), null, null, 300);
    }
}
