package com.marketpulse.reports;

import com.marketpulse.holdings.HoldingCheck;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** The latest holdings health check, exits first. */
public record HoldingsReport(Instant generatedAt, LocalDate session, List<HoldingCheck> checks, List<String> failed) {
}
