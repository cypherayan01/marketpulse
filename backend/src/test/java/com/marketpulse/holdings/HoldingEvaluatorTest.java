package com.marketpulse.holdings;

import com.marketpulse.market.Candle;
import com.marketpulse.market.TestCandles;
import com.marketpulse.signals.TradeStyle;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HoldingEvaluatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);
    private static final MarketReturns NIFTY = new MarketReturns(0.03, 0.06);
    private final HoldingEvaluator evaluator = new HoldingEvaluator();

    @Test
    void healthyUptrendIsAHoldWithProfitAndLoss() {
        List<Candle> candles = TestCandles.trend(300, 100, 0.002, 1_000, TODAY);
        double price = candles.getLast().close();
        Holding holding = holding(TradeStyle.SWING, 10, price * 0.95, TODAY.minusDays(20), price * 0.80, null);

        HoldingCheck check = evaluator.evaluate(holding, candles, NIFTY, null);

        assertThat(check.verdict()).isEqualTo(Verdict.HOLD);
        assertThat(check.price()).isEqualTo(price);
        assertThat(check.pnlPct()).isCloseTo((1 / 0.95 - 1) * 100, within(1e-9));
        assertThat(check.pnlAmount()).isCloseTo(10 * price * 0.05, within(1e-6));
        assertThat(check.reasons()).isNotEmpty();
    }

    @Test
    void closeBelowYourStopIsAnExit() {
        List<Candle> candles = withLastClose(TestCandles.trend(300, 100, 0.002, 1_000, TODAY.minusDays(1)), 0.97);
        double price = candles.getLast().close();
        Holding holding = holding(TradeStyle.SWING, 10, price * 1.02, TODAY.minusDays(1), price * 1.01, null);

        HoldingCheck check = evaluator.evaluate(holding, candles, NIFTY, null);

        assertThat(check.verdict()).isEqualTo(Verdict.EXIT);
        assertThat(check.stopSource()).isEqualTo("your stop");
        assertThat(check.reasons().getFirst()).startsWith("Closed below your stop");
    }

    @Test
    void trailingStopProtectsGainsAfterASharpDrop() {
        // bought long ago, rallied, then an 8% drop: below (highest close since buying - 2 ATR)
        List<Candle> candles = withLastClose(TestCandles.trend(300, 100, 0.004, 1_000, TODAY.minusDays(1)), 0.92);
        Holding holding = holding(TradeStyle.SWING, 10, 150, TODAY.minusDays(120), null, null);

        HoldingCheck check = evaluator.evaluate(holding, candles, NIFTY, null);

        assertThat(check.verdict()).isEqualTo(Verdict.EXIT);
        assertThat(check.stopSource()).isEqualTo("trailing stop, 2 ATR below the highest close since you bought");
        assertThat(check.pnlPct()).isPositive(); // still in profit - the trailing stop locks part of it in
    }

    @Test
    void bearishSetupIsAReview() {
        List<Candle> candles = TestCandles.trend(300, 100, -0.003, 1_000, TODAY);
        double price = candles.getLast().close();
        Holding holding = holding(TradeStyle.SWING, 10, price, TODAY.minusDays(1), null, null); // just bought

        HoldingCheck check = evaluator.evaluate(holding, candles, NIFTY, null);

        assertThat(check.verdict()).isEqualTo(Verdict.REVIEW);
        assertThat(check.reasons()).anyMatch(r -> r.contains("turned bearish") || r.contains("Below the 50-day EMA"));
    }

    @Test
    void resultsDueSoonIsAReview() {
        List<Candle> candles = TestCandles.trend(300, 100, 0.002, 1_000, TODAY);
        double price = candles.getLast().close();
        Holding holding = holding(TradeStyle.SWING, 10, price * 0.95, TODAY.minusDays(20), price * 0.80, null);

        HoldingCheck check = evaluator.evaluate(holding, candles, NIFTY, TODAY.plusDays(5));

        assertThat(check.verdict()).isEqualTo(Verdict.REVIEW);
        assertThat(check.reasons()).anyMatch(r -> r.contains("Results on 16 Nov"));
    }

    @Test
    void targetReachedIsNoted() {
        List<Candle> candles = TestCandles.trend(300, 100, 0.002, 1_000, TODAY);
        double price = candles.getLast().close();
        Holding holding = holding(TradeStyle.SWING, 10, price * 0.90, TODAY.minusDays(30), price * 0.80, price * 0.98);

        HoldingCheck check = evaluator.evaluate(holding, candles, NIFTY, null);

        assertThat(check.verdict()).isEqualTo(Verdict.HOLD);
        assertThat(check.notes()).anyMatch(n -> n.startsWith("Target"));
    }

    @Test
    void positionalHoldingsAreJudgedOnTheWeeklyChart() {
        List<Candle> candles = TestCandles.trend(600, 100, 0.002, 1_000, TODAY);
        double price = candles.getLast().close();
        Holding holding = holding(TradeStyle.POSITIONAL, 5, price * 0.80, TODAY.minusDays(90), null, null);

        HoldingCheck check = evaluator.evaluate(holding, candles, NIFTY, null);

        assertThat(check.verdict()).isEqualTo(Verdict.HOLD);
        assertThat(check.reasons()).anyMatch(r -> r.contains("40-week"));
        assertThat(check.stopSource()).contains("3 ATR");
    }

    private static Holding holding(TradeStyle horizon, int qty, double buyPrice, LocalDate buyDate, Double stop,
                                   Double target) {
        return new Holding(1L, "TEST", qty, buyPrice, buyDate, horizon, stop, target, Instant.EPOCH);
    }

    /** Appends today's candle closing at {@code factor} x the previous close. */
    private static List<Candle> withLastClose(List<Candle> history, double factor) {
        List<Candle> candles = new ArrayList<>(history);
        double prev = history.getLast().close();
        double close = prev * factor;
        candles.add(new Candle(TODAY, prev, prev, close * 0.995, close, 1_000));
        return candles;
    }
}
