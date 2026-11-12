package com.marketpulse.btst;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Share-price window for BTST picks (e.g. only stocks costing ₹100-₹800), so the list fits the money available.
 * Either end may be null (open).
 */
public record PriceRange(Double minPrice, Double maxPrice) {

    public static final PriceRange ANY = new PriceRange(null, null);

    public PriceRange {
        if (minPrice != null && minPrice <= 0 || maxPrice != null && maxPrice <= 0) {
            throw new IllegalArgumentException("Prices must be above ₹0");
        }
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new IllegalArgumentException("Minimum price must not be above the maximum price");
        }
    }

    public boolean contains(double price) {
        return (minPrice == null || price >= minPrice) && (maxPrice == null || price <= maxPrice);
    }

    public boolean isAny() {
        return minPrice == null && maxPrice == null;
    }

    public String label() {
        if (isAny()) {
            return "any price";
        }
        if (minPrice == null) {
            return "up to " + rupees(maxPrice);
        }
        if (maxPrice == null) {
            return rupees(minPrice) + " and above";
        }
        return rupees(minPrice) + " - " + rupees(maxPrice);
    }

    private static String rupees(double amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.of("en", "IN"));
        format.setMaximumFractionDigits(2);
        return "₹" + format.format(amount);
    }
}
