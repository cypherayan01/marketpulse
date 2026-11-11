package com.marketpulse.tracking;

public enum TradeStatus {
    OPEN, TARGET_HIT, STOPPED, EXPIRED;

    public boolean closed() {
        return this != OPEN;
    }
}
