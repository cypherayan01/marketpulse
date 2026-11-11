package com.marketpulse.news;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Bounded, in-memory memory of news ids already processed. Oldest entries are evicted first. */
public class SeenNewsRegistry {

    private final Set<String> seen;

    public SeenNewsRegistry(int capacity) {
        this.seen = Collections.synchronizedSet(Collections.newSetFromMap(new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > capacity;
            }
        }));
    }

    public boolean hasSeen(String id) {
        return seen.contains(id);
    }

    public void markSeen(String id) {
        seen.add(id);
    }
}
