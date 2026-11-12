package com.marketpulse.tracking;

import com.marketpulse.reports.ScanReport;
import com.marketpulse.reports.Section;
import com.marketpulse.signals.TradeSignal;
import com.marketpulse.universe.Segment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Listens to saved scans and opens a paper trade for every recommended (bullish) setup, in every segment. */
public class SignalRecorder implements Consumer<ScanReport> {

    private static final Logger log = LoggerFactory.getLogger(SignalRecorder.class);

    private final SignalRepository repository;
    private final Function<String, Segment> segments;

    public SignalRecorder(SignalRepository repository) {
        this(repository, symbol -> null);
    }

    public SignalRecorder(SignalRepository repository, Function<String, Segment> segments) {
        this.repository = repository;
        this.segments = segments;
    }

    @Override
    public void accept(ScanReport report) {
        List<TradeSignal> setups = new ArrayList<>(report.bullish());
        for (Section section : report.sections()) {
            setups.addAll(section.bullish());
        }
        int recorded = 0;
        for (TradeSignal s : setups) {
            if (s.plan() == null) {
                continue;
            }
            TrackedSignal trade = TrackedSignal.open(s.style(), s.symbol(), report.session(), s.score(),
                    s.classification(), s.confidence(), s.plan().entry(), s.plan().stopLoss(), s.plan().target(),
                    report.generatedAt()).withSegment(segments.apply(s.symbol()));
            if (repository.saveIfNew(trade)) { // duplicates (same stock listed twice) are ignored here
                recorded++;
            }
        }
        log.info("Recorded {} new {} paper trades", recorded, report.style());
    }
}
