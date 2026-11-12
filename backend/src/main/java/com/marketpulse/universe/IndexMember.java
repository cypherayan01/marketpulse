package com.marketpulse.universe;

/** One row of an NSE index constituent list. */
public record IndexMember(String symbol, String name, String industry) {
}
