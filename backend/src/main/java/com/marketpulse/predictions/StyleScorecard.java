package com.marketpulse.predictions;

import com.marketpulse.signals.TradeStyle;

import java.util.List;

/**
 * How one style's predictions turned out.
 *
 * @param keySessions the checkpoint the breakdowns use (BTST 1, swing 5, positional 20 sessions)
 */
public record StyleScorecard(TradeStyle style, int predictions, List<Checkpoint> checkpoints, int keySessions,
                             List<Breakdown> byCall, List<Breakdown> byScore, List<Breakdown> bySegment,
                             List<Breakdown> byNews, NextMorning nextMorning) {
}
