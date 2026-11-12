package com.marketpulse.tracking;

import com.marketpulse.market.Candle;
import com.marketpulse.market.MarketDataClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** After the close: move every open paper trade forward with the latest daily candles. */
public class SignalTracker {

    private static final Logger log = LoggerFactory.getLogger(SignalTracker.class);

    private final SignalRepository repository;
    private final MarketDataClient market;
    private final PaperTradeEvaluator evaluator;

    private final Object runLock = new Object();

    public SignalTracker(SignalRepository repository, MarketDataClient market, PaperTradeEvaluator evaluator) {
        this.repository = repository;
        this.market = market;
        this.evaluator = evaluator;
    }

    public TrackingRunSummary update() {
        synchronized (runLock) { // private lock: outside code can never hold it
            return updateLocked();
        }
    }

    private TrackingRunSummary updateLocked() {
        List<TrackedSignal> open = repository.findOpen();
        Map<String, List<Candle>> candles = new HashMap<>();
        int closed = 0;
        int failed = 0;
        for (TrackedSignal trade : open) {
            List<Candle> history;
            try {
                history = candles.computeIfAbsent(trade.symbol(), market::dailyCandles);
            } catch (RuntimeException e) {
                log.warn("No data to track {}: {}", trade.symbol(), e.getMessage());
                failed++;
                continue;
            }
            TrackedSignal updated = evaluator.evaluate(trade, history);
            if (!updated.equals(trade)) {
                repository.update(updated);
                if (updated.status().closed()) {
                    closed++;
                    log.info("Paper trade closed: {} {} {} {}%", updated.style(), updated.symbol(), updated.status(),
                            String.format("%.2f", updated.returnPct()));
                }
            }
        }
        TrackingRunSummary summary = new TrackingRunSummary(open.size(), closed, failed);
        log.info("Tracking finished: {}", summary);
        return summary;
    }
}
