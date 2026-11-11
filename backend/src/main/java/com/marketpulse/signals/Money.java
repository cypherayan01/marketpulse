package com.marketpulse.signals;

import java.util.Locale;

public final class Money {

    private Money() {
    }

    /** ₹1,234.50 */
    public static String rupees(double price) {
        return String.format(Locale.ENGLISH, "₹%,.2f", price);
    }
}
