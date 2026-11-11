package com.marketpulse.swing;

import java.time.LocalDate;

/** Test builder for {@link IndicatorSnapshot}; defaults describe a textbook bullish breakout. */
class SnapshotBuilder {
    String symbol = "TEST";
    double close = 110, ema20 = 105, ema50 = 100, ema200 = 90;
    double rsi = 60, macd = 2, macdSignal = 1;
    MacdCross cross = MacdCross.BULLISH;
    double atr = 3;
    long volume = 3_000;
    double avgVolume = 1_500, prior20High = 108, prior20Low = 95, high52w = 112, swingLow10 = 103;
    double return3m = 0.15;
    int bars = 400;

    static SnapshotBuilder bullishBreakout() {
        return new SnapshotBuilder();
    }

    static SnapshotBuilder bearishBreakdown() {
        SnapshotBuilder b = new SnapshotBuilder();
        b.close = 80; b.ema20 = 85; b.ema50 = 90; b.ema200 = 100;
        b.rsi = 35; b.macd = -2; b.macdSignal = -1; b.cross = MacdCross.BEARISH;
        b.prior20High = 92; b.prior20Low = 82; b.high52w = 120; b.swingLow10 = 79;
        b.return3m = -0.15;
        return b;
    }

    IndicatorSnapshot build() {
        return new IndicatorSnapshot(symbol, LocalDate.of(2026, 11, 11), close, ema20, ema50, ema200, rsi, macd,
                macdSignal, cross, atr, volume, avgVolume, prior20High, prior20Low, high52w, swingLow10, return3m, bars);
    }
}
