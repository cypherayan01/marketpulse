package com.marketpulse.news;

import java.util.List;

/** Assesses the market impact of a batch of headlines. */
public interface NewsImpactAnalyzer {
    List<NewsImpact> analyze(List<NewsItem> items);
}
