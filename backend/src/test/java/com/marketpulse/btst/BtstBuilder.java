package com.marketpulse.btst;

import java.time.LocalDate;

/** Test builder for {@link BtstSnapshot}; defaults describe a textbook BTST candidate. */
class BtstBuilder {
    double price = 103, dayOpen = 100, dayHigh = 103.2, dayLow = 99.8, prevClose = 100;
    long volume = 1_840;
    double avgVolume = 1_000, sessionFraction = 0.92;      // on pace for 2.0x average volume
    double prior20High = 102, ema20 = 100, ema50 = 97, rsi = 65, atr = 2, lastHour = 0.8;
    int bars = 400;

    static BtstBuilder strongClose() {
        return new BtstBuilder();
    }

    static BtstBuilder weakClose() {
        BtstBuilder b = new BtstBuilder();
        b.price = 97.2; b.dayOpen = 100; b.dayHigh = 100.5; b.dayLow = 97; b.prevClose = 100; // -2.8%, near the low
        b.volume = 1_840; b.ema20 = 99; b.ema50 = 101; b.rsi = 40; b.lastHour = -0.9;
        return b;
    }

    BtstSnapshot build() {
        return new BtstSnapshot("TEST", LocalDate.of(2026, 11, 11), price, dayOpen, dayHigh, dayLow, prevClose,
                volume, avgVolume, sessionFraction, prior20High, ema20, ema50, rsi, atr, lastHour, bars);
    }
}
