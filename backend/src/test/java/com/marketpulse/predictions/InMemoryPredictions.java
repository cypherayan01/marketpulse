package com.marketpulse.predictions;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Same rules as the JDBC repository, in memory. */
class InMemoryPredictions implements PredictionRepository {

    final List<Prediction> rows = new ArrayList<>();
    private long nextId = 1;

    @Override
    public boolean saveIfNew(Prediction p) {
        boolean exists = rows.stream().anyMatch(r -> r.source() == p.source() && r.style() == p.style()
                && r.symbol().equals(p.symbol()) && r.signalDate().equals(p.signalDate()) && r.call() == p.call());
        if (exists) return false;
        rows.add(p.withId(nextId++));
        return true;
    }

    @Override
    public void replaceReplay(List<Prediction> predictions) {
        rows.removeIf(r -> r.source() == PredictionSource.REPLAY);
        predictions.forEach(this::saveIfNew);
    }

    @Override
    public List<Prediction> findPending(PredictionSource source, LocalDate since) {
        return rows.stream().filter(r -> r.source() == source && !r.signalDate().isBefore(since) && !r.complete()).toList();
    }

    @Override
    public List<Prediction> findAll(PredictionSource source) {
        return rows.stream().filter(r -> r.source() == source)
                .sorted(Comparator.comparing(Prediction::signalDate).reversed()).toList();
    }

    @Override
    public List<Prediction> findByDate(PredictionSource source, LocalDate date) {
        return rows.stream().filter(r -> r.source() == source && r.signalDate().equals(date)).toList();
    }

    @Override
    public List<LocalDate> findDates(PredictionSource source) {
        return rows.stream().filter(r -> r.source() == source).map(Prediction::signalDate).distinct()
                .sorted(Comparator.reverseOrder()).toList();
    }

    @Override
    public void update(Prediction p) {
        rows.replaceAll(r -> r.id().equals(p.id()) ? p : r);
    }
}
