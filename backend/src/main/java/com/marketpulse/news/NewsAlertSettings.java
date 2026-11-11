package com.marketpulse.news;

import java.time.Duration;

/**
 * @param lookback        only news published within this window is considered (protects against re-alerting after a restart)
 * @param impactThreshold minimum impact score (1-10) that triggers an alert
 * @param batchSize       headlines sent to the analyzer per call
 */
public record NewsAlertSettings(Duration lookback, int impactThreshold, int batchSize) {
}
