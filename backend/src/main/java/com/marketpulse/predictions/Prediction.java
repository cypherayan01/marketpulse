package com.marketpulse.predictions;

import com.marketpulse.signals.Classification;
import com.marketpulse.signals.TradeStyle;
import com.marketpulse.universe.Segment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One prediction and what happened after it.
 *
 * @param price         the stock's close on the signal day
 * @param niftyPrice    Nifty's close on the signal day
 * @param newsSentiment scored news about the stock in the days before: +1 bullish, -1 bearish, 0 none or mixed
 * @param returns       stock % change after N sessions (keys from {@link #CHECKPOINTS}), filled in as days pass
 * @param niftyReturns  Nifty % change over the same dates
 * @param nextOpenPct   next session's open vs the signal close, % (the BTST exit)
 * @param nextHighPct   next session's high vs the signal close, %
 */
public record Prediction(Long id, PredictionSource source, TradeStyle style, String symbol, Segment segment,
                         LocalDate signalDate, int score, Classification classification, Call call,
                         double price, double niftyPrice, int newsSentiment,
                         Map<Integer, Double> returns, Map<Integer, Double> niftyReturns,
                         Double nextOpenPct, Double nextHighPct, Instant createdAt) {

    /** Sessions after the signal at which a prediction is checked. */
    public static final List<Integer> CHECKPOINTS = List.of(1, 2, 3, 5, 10, 20, 40, 60);

    public Prediction {
        returns = Map.copyOf(returns);
        niftyReturns = Map.copyOf(niftyReturns);
    }

    public static Prediction of(PredictionSource source, TradeStyle style, String symbol, Segment segment,
                                LocalDate signalDate, int score, Classification classification, Call call,
                                double price, double niftyPrice, int newsSentiment, Instant createdAt) {
        return new Prediction(null, source, style, symbol, segment, signalDate, score, classification, call, price,
                niftyPrice, newsSentiment, Map.of(), Map.of(), null, null, createdAt);
    }

    public Prediction withOutcomes(Map<Integer, Double> newReturns, Map<Integer, Double> newNiftyReturns,
                                   Double newNextOpenPct, Double newNextHighPct) {
        return new Prediction(id, source, style, symbol, segment, signalDate, score, classification, call, price,
                niftyPrice, newsSentiment, newReturns, newNiftyReturns, newNextOpenPct, newNextHighPct, createdAt);
    }

    public Prediction withId(long newId) {
        return new Prediction(newId, source, style, symbol, segment, signalDate, score, classification, call, price,
                niftyPrice, newsSentiment, returns, niftyReturns, nextOpenPct, nextHighPct, createdAt);
    }

    /**
     * How much better than Nifty the call turned out, in % points: for a BUY the stock's lead over Nifty,
     * for an AVOID how far it lagged Nifty. Positive = the call was right.
     */
    public Double edgePct(int sessions) {
        Double stock = returns.get(sessions);
        Double nifty = niftyReturns.get(sessions);
        if (stock == null || nifty == null) {
            return null;
        }
        return call == Call.BUY ? stock - nifty : nifty - stock;
    }

    /** Right = beat the market (BUY) or lagged it (AVOID). Empty until that checkpoint has passed. */
    public Optional<Boolean> correct(int sessions) {
        return Optional.ofNullable(edgePct(sessions)).map(edge -> edge > 0);
    }

    /** All checkpoints measured - nothing more to fill in. */
    public boolean complete() {
        return returns.keySet().containsAll(CHECKPOINTS);
    }
}
