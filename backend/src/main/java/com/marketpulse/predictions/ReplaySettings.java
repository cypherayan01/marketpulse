package com.marketpulse.predictions;

/**
 * @param sessions       how many past trading days to replay (~250 = a year)
 * @param perSection     buy and avoid calls per market-cap section and day, as in the live reports
 * @param btstPerSection strong BTST calls per section and day
 */
public record ReplaySettings(int sessions, int perSection, int btstPerSection) {
}
