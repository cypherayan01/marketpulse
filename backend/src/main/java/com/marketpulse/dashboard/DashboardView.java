package com.marketpulse.dashboard;

import com.marketpulse.events.MarketEvent;
import com.marketpulse.reports.BriefReport;
import com.marketpulse.reports.NewsEntry;
import com.marketpulse.reports.ScanReport;

import java.time.Instant;
import java.util.List;

/** Everything the dashboard shows, in one response. Reports are null until their job has run. */
public record DashboardView(Instant serverTime, BriefReport brief, ScanReport swing, ScanReport positional,
                            ScanReport btst, List<NewsEntry> news, List<MarketEvent> events) {
}
