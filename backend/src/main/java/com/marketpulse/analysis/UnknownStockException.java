package com.marketpulse.analysis;

public class UnknownStockException extends RuntimeException {
    public UnknownStockException(String symbol) {
        super("No NSE price data found for " + symbol + " - check the symbol");
    }
}
