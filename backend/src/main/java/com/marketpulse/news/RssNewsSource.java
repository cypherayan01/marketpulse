package com.marketpulse.news;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Reads headlines from an RSS/Atom feed. */
public class RssNewsSource implements NewsSource {

    private final String name;
    private final String url;
    private final RestClient restClient;

    public RssNewsSource(String name, String url, RestClient restClient) {
        this.name = name;
        this.url = url;
        this.restClient = restClient;
    }

    @Override
    public List<NewsItem> fetchLatest() {
        byte[] body = restClient.get().uri(url).retrieve().body(byte[].class);
        if (body == null) {
            return List.of();
        }
        try {
            return parse(name, new ByteArrayInputStream(body));
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse feed " + name + ": " + e.getMessage(), e);
        }
    }

    static List<NewsItem> parse(String sourceName, InputStream xml) throws Exception {
        SyndFeed feed = new SyndFeedInput().build(new XmlReader(xml));
        List<NewsItem> items = new ArrayList<>();
        for (SyndEntry entry : feed.getEntries()) {
            Date published = entry.getPublishedDate() != null ? entry.getPublishedDate() : entry.getUpdatedDate();
            if (published == null || entry.getTitle() == null) {
                continue; // without a date we cannot tell if it is fresh
            }
            String id = notBlank(entry.getUri()) ? entry.getUri() : entry.getLink();
            String summary = entry.getDescription() != null ? stripHtml(entry.getDescription().getValue()) : "";
            items.add(new NewsItem(id, entry.getTitle().trim(), summary, entry.getLink(), sourceName,
                    published.toInstant()));
        }
        return items;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String stripHtml(String html) {
        return html == null ? "" : html.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }
}
