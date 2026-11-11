package com.marketpulse.dashboard;

import com.marketpulse.tracking.StyleStats;
import com.marketpulse.tracking.TrackedSignal;

import java.util.List;

/** Paper-trading track record for the dashboard. */
public record PerformanceView(List<StyleStats> styles, List<TrackedSignal> open, List<TrackedSignal> recentClosed) {
}
