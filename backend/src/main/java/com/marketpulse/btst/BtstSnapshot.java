package com.marketpulse.btst;

import java.time.LocalDate;

/**
 * Today's session so far (around 3 PM) plus the daily context a BTST decision needs.
 *
 * @param sessionFraction share of the trading session elapsed (0-1), used to project today's full volume
 * @param avgVolume20     average daily volume of the 20 sessions before today
 * @param prior20High     highest high of the 20 sessions before today
 * @param lastHourChangePct price change over the last hour of 15-minute bars, in percent
 */
public record BtstSnapshot(String symbol, LocalDate date, double price,
                           double dayOpen, double dayHigh, double dayLow, double prevClose,
                           long volume, double avgVolume20, double sessionFraction,
                           double prior20High, double ema20, double ema50, double rsi14, double atr14,
                           double lastHourChangePct, int barCount) {

    public double dayChangePct() {
        return (price / prevClose - 1) * 100;
    }

    /** Where the price sits in today's range: 0 = at the low, 1 = at the high. */
    public double closeLocation() {
        double range = dayHigh - dayLow;
        return range <= 0 ? 0.5 : (price - dayLow) / range;
    }

    /** Today's volume extrapolated to a full session, relative to the 20-day average. */
    public double projectedVolumeRatio() {
        return avgVolume20 > 0 && sessionFraction > 0 ? volume / sessionFraction / avgVolume20 : 0;
    }
}
