package com.marketpulse.reports;

import com.marketpulse.signals.TradeSignal;
import com.marketpulse.universe.Segment;
import com.marketpulse.universe.Universe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Splits scored stocks into large / mid / small / other sections. */
public final class SectionBuilder {

    private SectionBuilder() {
    }

    public static List<Section> build(List<TradeSignal> signals, Universe universe, int perSection,
                                      Map<Segment, String> indexLines) {
        List<Section> sections = new ArrayList<>();
        for (Segment segment : Segment.values()) {
            List<TradeSignal> inSegment = signals.stream()
                    .filter(s -> universe.segmentOf(s.symbol()) == segment)
                    .toList();
            if (!inSegment.isEmpty()) {
                sections.add(new Section(segment, indexLines.get(segment), inSegment.size(),
                        topBullish(inSegment, perSection), topBearish(inSegment, perSection)));
            }
        }
        return sections;
    }

    public static List<TradeSignal> topBullish(List<TradeSignal> signals, int limit) {
        return signals.stream()
                .filter(s -> s.classification().bullish())
                .sorted(Comparator.comparingInt(TradeSignal::score).reversed())
                .limit(limit)
                .toList();
    }

    public static List<TradeSignal> topBearish(List<TradeSignal> signals, int limit) {
        return signals.stream()
                .filter(s -> s.classification().bearish())
                .sorted(Comparator.comparingInt(TradeSignal::score))
                .limit(limit)
                .toList();
    }
}
