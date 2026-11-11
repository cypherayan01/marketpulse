package com.marketpulse.positional;

import java.time.LocalDate;

/** Test builder for {@link PositionalSnapshot}; defaults describe a textbook long-term breakout. */
class PositionalBuilder {
    double close = 500, ema10 = 480, ema30 = 450, sma40 = 420, sma40Prev = 410;
    double rsi = 65, macd = 5, macdSignal = 3, atr = 20;
    long volume = 3_000_000;
    double avgVolume = 1_500_000, prior26High = 490, prior26Low = 380, high52w = 505, swingLow8w = 450;
    double return6m = 0.30;
    int weeks = 104;

    static PositionalBuilder bullishBreakout() {
        return new PositionalBuilder();
    }

    static PositionalBuilder bearishBreakdown() {
        PositionalBuilder b = new PositionalBuilder();
        b.close = 300; b.ema10 = 320; b.ema30 = 340; b.sma40 = 360; b.sma40Prev = 370;
        b.rsi = 35; b.macd = -5; b.macdSignal = -3;
        b.prior26High = 420; b.prior26Low = 310; b.high52w = 480; b.swingLow8w = 295;
        b.return6m = -0.25;
        return b;
    }

    PositionalSnapshot build() {
        return new PositionalSnapshot("TEST", LocalDate.of(2026, 11, 13), close, ema10, ema30, sma40, sma40Prev, rsi,
                macd, macdSignal, atr, volume, avgVolume, prior26High, prior26Low, high52w, swingLow8w, return6m, weeks);
    }
}
