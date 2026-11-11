package com.marketpulse.holdings;

import com.marketpulse.reports.HoldingsReport;

import java.util.List;

/** Your holdings plus the latest health check (null until it has run). */
public record HoldingsView(List<Holding> holdings, HoldingsReport latestCheck) {
}
