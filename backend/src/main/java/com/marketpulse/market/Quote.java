package com.marketpulse.market;

/** Latest price and the previous session's close. */
public record Quote(double price, double previousClose) {

    public double changePct() {
        return (price / previousClose - 1) * 100;
    }
}
