package com.marketpulse.predictions;

import com.marketpulse.market.Candle;
import com.marketpulse.market.MarketDataClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** After the close: fill in every LIVE prediction's checkpoints that have now passed. */
public class PredictionUpdater {

    private static final Logger log = LoggerFactory.getLogger(PredictionUpdater.class);
    /** The last checkpoint (60 sessions) is ~3 months away; stop looking after 4. */
    private static final int LOOKBACK_DAYS = 120;

    private final PredictionRepository repository;
    private final MarketDataClient market;
    private final OutcomeCalculator outcomes;
    private final String indexSymbol;
    private final Clock clock;

    private final Object runLock = new Object();

    public PredictionUpdater(PredictionRepository repository, MarketDataClient market, OutcomeCalculator outcomes,
                             String indexSymbol, Clock clock) {
        this.repository = repository;
        this.market = market;
        this.outcomes = outcomes;
        this.indexSymbol = indexSymbol;
        this.clock = clock;
    }

    public PredictionUpdateSummary update() {
        synchronized (runLock) { // private lock: outside code can never hold it
            return updateLocked();
        }
    }

    private PredictionUpdateSummary updateLocked() {
        LocalDate since = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).minusDays(LOOKBACK_DAYS);
        List<Prediction> pending = repository.findPending(PredictionSource.LIVE, since);
        if (pending.isEmpty()) {
            return new PredictionUpdateSummary(0, 0, 0);
        }
        List<Candle> nifty = market.dailyCandles(indexSymbol);
        Map<String, List<Candle>> candles = new HashMap<>();
        int updated = 0;
        int failed = 0;
        for (Prediction p : pending) {
            List<Candle> history;
            try {
                history = candles.computeIfAbsent(p.symbol(), market::dailyCandles);
            } catch (RuntimeException e) {
                failed++;
                continue;
            }
            Prediction filled = outcomes.fill(p, history, nifty);
            if (!filled.equals(p)) {
                repository.update(filled);
                updated++;
            }
        }
        PredictionUpdateSummary summary = new PredictionUpdateSummary(pending.size(), updated, failed);
        log.info("Predictions updated: {}", summary);
        return summary;
    }
}
