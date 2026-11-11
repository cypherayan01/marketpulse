package com.marketpulse.signals;

public enum Classification {
    STRONG_BULLISH, BULLISH, NEUTRAL, BEARISH, STRONG_BEARISH;

    public static Classification of(int score) {
        if (score >= 80) return STRONG_BULLISH;
        if (score >= 65) return BULLISH;
        if (score >= 45) return NEUTRAL;
        if (score >= 25) return BEARISH;
        return STRONG_BEARISH;
    }

    public boolean bullish() {
        return this == STRONG_BULLISH || this == BULLISH;
    }

    public boolean bearish() {
        return this == STRONG_BEARISH || this == BEARISH;
    }

    public String label() {
        return name().replace('_', ' ');
    }
}
