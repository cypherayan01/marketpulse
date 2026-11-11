package com.marketpulse.news;

import java.util.List;

/** Anything that can provide the latest headlines (an RSS feed, a news API, ...). */
@FunctionalInterface
public interface NewsSource {
    List<NewsItem> fetchLatest();
}
