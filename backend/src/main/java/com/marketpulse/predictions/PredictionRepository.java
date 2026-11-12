package com.marketpulse.predictions;

import java.time.LocalDate;
import java.util.List;

public interface PredictionRepository {

    /** @return false if this exact prediction (source, style, symbol, day, call) was stored already */
    boolean saveIfNew(Prediction prediction);

    /** Replaces every REPLAY prediction with these (duplicates in the list keep the first). */
    void replaceReplay(List<Prediction> predictions);

    /** Predictions from {@code since} on that still have checkpoints to fill in. */
    List<Prediction> findPending(PredictionSource source, LocalDate since);

    List<Prediction> findAll(PredictionSource source);

    List<Prediction> findByDate(PredictionSource source, LocalDate date);

    /** Days that have predictions, newest first. */
    List<LocalDate> findDates(PredictionSource source);

    void update(Prediction prediction);
}
