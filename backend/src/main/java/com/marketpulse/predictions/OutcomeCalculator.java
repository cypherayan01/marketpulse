package com.marketpulse.predictions;

import com.marketpulse.market.Candle;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fills in what happened after a prediction: the stock's move after 1, 2, 3, 5, 10, 20, 40 and 60 sessions,
 * Nifty's move over exactly the same dates, and the next morning's open and high.
 */
public class OutcomeCalculator {

    public Prediction fill(Prediction p, List<Candle> stock, List<Candle> nifty) {
        int signal = indexOf(stock, p.signalDate());
        if (signal < 0) {
            return p;
        }
        Map<LocalDate, Candle> niftyByDate = nifty.stream()
                .collect(Collectors.toMap(Candle::date, Function.identity(), (a, b) -> b));
        Candle niftyAtSignal = niftyByDate.get(p.signalDate());
        double niftyBase = niftyAtSignal != null ? niftyAtSignal.close() : p.niftyPrice();

        Map<Integer, Double> returns = new HashMap<>();
        Map<Integer, Double> niftyReturns = new HashMap<>();
        for (int k : Prediction.CHECKPOINTS) {
            if (signal + k >= stock.size()) {
                break;
            }
            Candle later = stock.get(signal + k);
            Candle niftyLater = niftyByDate.get(later.date());
            if (niftyLater == null || niftyBase <= 0) {
                continue; // can't compare fairly without Nifty on the same date
            }
            returns.put(k, pct(later.close(), p.price()));
            niftyReturns.put(k, pct(niftyLater.close(), niftyBase));
        }

        Double nextOpen = null;
        Double nextHigh = null;
        if (signal + 1 < stock.size()) {
            Candle next = stock.get(signal + 1);
            nextOpen = pct(next.open(), p.price());
            nextHigh = pct(next.high(), p.price());
        }
        return p.withOutcomes(returns, niftyReturns, nextOpen, nextHigh);
    }

    private static int indexOf(List<Candle> candles, LocalDate date) {
        for (int i = candles.size() - 1; i >= 0; i--) {
            if (candles.get(i).date().equals(date)) {
                return i;
            }
        }
        return -1;
    }

    private static double pct(double now, double then) {
        return (now / then - 1) * 100;
    }
}
