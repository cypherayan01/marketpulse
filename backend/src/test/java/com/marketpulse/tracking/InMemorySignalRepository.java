package com.marketpulse.tracking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Same rules as the JDBC repository, in memory, for fast unit tests. */
class InMemorySignalRepository implements SignalRepository {

    final List<TrackedSignal> rows = new ArrayList<>();
    private long nextId = 1;

    @Override
    public boolean saveIfNew(TrackedSignal s) {
        boolean clash = rows.stream().anyMatch(r -> r.style() == s.style() && r.symbol().equals(s.symbol())
                && (r.status() == TradeStatus.OPEN || r.signalDate().equals(s.signalDate())));
        if (clash) {
            return false;
        }
        rows.add(s.withId(nextId++));
        return true;
    }

    @Override
    public List<TrackedSignal> findOpen() {
        return rows.stream().filter(r -> r.status() == TradeStatus.OPEN).toList();
    }

    @Override
    public List<TrackedSignal> findAll() {
        return rows.stream().sorted(Comparator.comparing(TrackedSignal::signalDate).reversed()).toList();
    }

    @Override
    public void update(TrackedSignal s) {
        rows.replaceAll(r -> r.id().equals(s.id()) ? s : r);
    }
}
