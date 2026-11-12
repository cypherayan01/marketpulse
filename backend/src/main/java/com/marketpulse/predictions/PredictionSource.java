package com.marketpulse.predictions;

/** LIVE = made by a scheduled scan; REPLAY = reconstructed by replaying past days (an estimate). */
public enum PredictionSource {
    LIVE, REPLAY
}
