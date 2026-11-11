package com.marketpulse.tracking;

import com.marketpulse.reports.ScanReport;
import com.marketpulse.signals.TradeSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/** Listens to saved scans and opens a paper trade for every recommended (bullish) setup. */
public class SignalRecorder implements Consumer<ScanReport> {

    private static final Logger log = LoggerFactory.getLogger(SignalRecorder.class);

    private final SignalRepository repository;

    public SignalRecorder(SignalRepository repository) {
        this.repository = repository;
    }

    @Override
    public void accept(ScanReport report) {
        int recorded = 0;
        for (TradeSignal s : report.bullish()) {
            if (s.plan() == null) {
                continue;
            }
            TrackedSignal trade = TrackedSignal.open(s.style(), s.symbol(), report.session(), s.score(),
                    s.classification(), s.confidence(), s.plan().entry(), s.plan().stopLoss(), s.plan().target(),
                    report.generatedAt());
            if (repository.saveIfNew(trade)) {
                recorded++;
            }
        }
        log.info("Recorded {} new {} paper trades", recorded, report.style());
    }
}
