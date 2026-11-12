package com.marketpulse.universe;

/**
 * Market-cap segment a stock is reported under. Each stock goes to the first segment whose index contains it.
 *
 * @param indexSymbol Yahoo symbol of the segment's index, for context in reports
 */
public enum Segment {
    LARGE("Large cap", "Nifty 100", "^CNX100", "🔵"),
    MID("Mid cap", "Nifty Midcap 100", "NIFTY_MIDCAP_100.NS", "🟣"),
    SMALL("Small cap", "Nifty Smallcap 100", "^CNXSC", "🟠"),
    OTHER("Other Nifty 500", "Nifty 500", "^CRSLDX", "⚪");

    private final String title;
    private final String indexName;
    private final String indexSymbol;
    private final String icon;

    Segment(String title, String indexName, String indexSymbol, String icon) {
        this.title = title;
        this.indexName = indexName;
        this.indexSymbol = indexSymbol;
        this.icon = icon;
    }

    public String title() { return title; }
    public String indexName() { return indexName; }
    public String indexSymbol() { return indexSymbol; }
    public String icon() { return icon; }
}
