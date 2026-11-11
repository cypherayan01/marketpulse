package com.marketpulse.brief;

/** Turns the morning's facts into a readable brief. */
@FunctionalInterface
public interface BriefWriter {
    String write(BriefInput input);
}
