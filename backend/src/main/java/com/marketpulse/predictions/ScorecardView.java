package com.marketpulse.predictions;

import java.time.LocalDate;
import java.util.List;

/** @param caveat shown with replayed results, which are an estimate */
public record ScorecardView(PredictionSource source, int predictions, LocalDate firstDate, LocalDate lastDate,
                            List<StyleScorecard> styles, String caveat) {
}
