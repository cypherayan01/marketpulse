package com.marketpulse.predictions;

/** @param checked pending predictions looked at · updated ones that got new checkpoints · failed stocks without data */
public record PredictionUpdateSummary(int checked, int updated, int failed) {
}
