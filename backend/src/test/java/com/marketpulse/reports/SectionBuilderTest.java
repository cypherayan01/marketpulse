package com.marketpulse.reports;

import com.marketpulse.signals.Classification;
import com.marketpulse.signals.Confidence;
import com.marketpulse.signals.TradeSignal;
import com.marketpulse.signals.TradeStyle;
import com.marketpulse.universe.IndexMember;
import com.marketpulse.universe.Segment;
import com.marketpulse.universe.Universe;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SectionBuilderTest {

    private final Universe universe = Universe.of(
            List.of(m("TCS"), m("INFY"), m("HDFCBANK")),
            List.of(m("PERSISTENT"), m("COFORGE")),
            List.of(m("AARTIIND")),
            List.of(m("TCS"), m("INFY"), m("HDFCBANK"), m("PERSISTENT"), m("COFORGE"), m("AARTIIND"), m("JINDALSAW")));

    @Test
    void groupsSignalsBySegmentWithBullishBestFirstAndBearishWorstFirst() {
        List<TradeSignal> signals = List.of(s("TCS", 70), s("INFY", 90), s("HDFCBANK", 20), s("PERSISTENT", 85),
                s("COFORGE", 30), s("AARTIIND", 50), s("JINDALSAW", 80));

        List<Section> sections = SectionBuilder.build(signals, universe, 5, Map.of(Segment.LARGE, "Nifty 100 +0.62% today"));

        assertThat(sections).extracting(Section::segment)
                .containsExactly(Segment.LARGE, Segment.MID, Segment.SMALL, Segment.OTHER);
        Section large = sections.getFirst();
        assertThat(large.indexLine()).isEqualTo("Nifty 100 +0.62% today");
        assertThat(large.scanned()).isEqualTo(3);
        assertThat(large.bullish()).extracting(TradeSignal::symbol).containsExactly("INFY", "TCS");
        assertThat(large.bearish()).extracting(TradeSignal::symbol).containsExactly("HDFCBANK");
        assertThat(sections.get(2).bullish()).isEmpty(); // AARTIIND is neutral
        assertThat(sections.get(3).bullish()).extracting(TradeSignal::symbol).containsExactly("JINDALSAW");
    }

    @Test
    void limitsEachListPerSection() {
        List<TradeSignal> signals = List.of(s("TCS", 70), s("INFY", 90), s("HDFCBANK", 80));

        assertThat(SectionBuilder.build(signals, universe, 2, Map.of()).getFirst().bullish())
                .extracting(TradeSignal::symbol).containsExactly("INFY", "HDFCBANK");
    }

    @Test
    void leavesOutSegmentsWithNothingScanned() {
        List<Section> sections = SectionBuilder.build(List.of(s("TCS", 70)), universe, 5, Map.of());

        assertThat(sections).extracting(Section::segment).containsExactly(Segment.LARGE);
    }

    @Test
    void picksTheBestAcrossAllSegments() {
        List<TradeSignal> signals = List.of(s("TCS", 70), s("PERSISTENT", 85), s("JINDALSAW", 80), s("COFORGE", 20));

        assertThat(SectionBuilder.topBullish(signals, 2)).extracting(TradeSignal::symbol)
                .containsExactly("PERSISTENT", "JINDALSAW");
        assertThat(SectionBuilder.topBearish(signals, 2)).extracting(TradeSignal::symbol).containsExactly("COFORGE");
    }

    private static IndexMember m(String symbol) {
        return new IndexMember(symbol, symbol, "");
    }

    private static TradeSignal s(String symbol, int score) {
        return new TradeSignal(symbol, TradeStyle.SWING, score, Classification.of(score), Confidence.HIGH,
                List.of(), List.of(), null, null, 100);
    }
}
