package com.marketpulse.tracking;

import java.util.List;

/** Storage for paper trades. */
public interface SignalRepository {

    /**
     * Saves a new open trade unless the same style already holds this stock, or the same signal
     * (style, symbol, day) was stored before. @return true if it was saved
     */
    boolean saveIfNew(TrackedSignal signal);

    List<TrackedSignal> findOpen();

    /** All trades, newest signal first. */
    List<TrackedSignal> findAll();

    void update(TrackedSignal signal);
}
