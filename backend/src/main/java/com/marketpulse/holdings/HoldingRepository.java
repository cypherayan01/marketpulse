package com.marketpulse.holdings;

import java.util.List;

public interface HoldingRepository {

    /** @return the holding with its new id */
    Holding add(Holding holding);

    /** Sorted by symbol. */
    List<Holding> findAll();

    /** @return false if there was no such holding */
    boolean delete(long id);
}
