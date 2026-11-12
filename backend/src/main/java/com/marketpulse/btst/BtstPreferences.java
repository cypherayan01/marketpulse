package com.marketpulse.btst;

/** BTST choices made on the dashboard, kept in the database so the 14:50 scan uses them too. */
public interface BtstPreferences {

    PriceRange priceRange();

    void savePriceRange(PriceRange range);
}
