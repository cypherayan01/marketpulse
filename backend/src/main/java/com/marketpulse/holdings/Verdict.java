package com.marketpulse.holdings;

/** Daily health verdict for a holding, most urgent first. */
public enum Verdict {
    /** Closed below the active stop. */
    EXIT,
    /** Trend or setup weakening, or results due - decide deliberately. */
    REVIEW,
    /** Still valid. */
    HOLD
}
