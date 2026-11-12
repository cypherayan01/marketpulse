package com.marketpulse.universe;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UniverseTest {

    @Test
    void parsesAnNseIndexListSkippingPlaceholdersAndBlankLines() {
        String csv = """
                Company Name,Industry,Symbol,Series,ISIN Code
                Aarti Industries Ltd.,Chemicals,AARTIIND,EQ,INE769A01020
                Dummy HEG Ltd.,Capital Goods,DUMMYHEG,EQ,DUM545A01024
                Mahindra & Mahindra Ltd.,Automobile and Auto Components,M&M,EQ,INE101A01026

                """;

        List<IndexMember> members = IndexListParser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(members).containsExactly(
                new IndexMember("AARTIIND", "Aarti Industries Ltd.", "Chemicals"),
                new IndexMember("M&M", "Mahindra & Mahindra Ltd.", "Automobile and Auto Components"));
    }

    @Test
    void assignsEachStockToTheFirstSegmentItBelongsTo() {
        Universe universe = Universe.of(
                List.of(member("TCS")),
                List.of(member("PERSISTENT")),
                List.of(member("AARTIIND")),
                List.of(member("TCS"), member("PERSISTENT"), member("AARTIIND"), member("JINDALSAW")));

        assertThat(universe.segmentOf("TCS")).isEqualTo(Segment.LARGE);
        assertThat(universe.segmentOf("PERSISTENT")).isEqualTo(Segment.MID);
        assertThat(universe.segmentOf("AARTIIND")).isEqualTo(Segment.SMALL);
        assertThat(universe.segmentOf("JINDALSAW")).isEqualTo(Segment.OTHER);
        assertThat(universe.symbols()).containsExactly("TCS", "PERSISTENT", "AARTIIND", "JINDALSAW");
    }

    @Test
    void unknownStocksAreOther() {
        Universe universe = Universe.of(List.of(member("TCS")), List.of(), List.of(), List.of());

        assertThat(universe.segmentOf("NOT_LISTED")).isEqualTo(Segment.OTHER);
        assertThat(universe.find("tcs")).map(Stock::segment).contains(Segment.LARGE);
    }

    @Test
    void aPlainSymbolListMakesAnUnsegmentedUniverse() {
        Universe universe = Universe.unsegmented(List.of("UPCO", "DOWNCO"));

        assertThat(universe.symbols()).containsExactly("UPCO", "DOWNCO");
        assertThat(universe.segmentOf("UPCO")).isEqualTo(Segment.OTHER);
    }

    @Test
    void loadsTheBundledNseIndexLists() {
        Universe universe = Universe.loadBundled();

        assertThat(universe.stocks(Segment.LARGE)).hasSize(100);
        assertThat(universe.stocks(Segment.MID)).hasSize(100);
        assertThat(universe.stocks(Segment.SMALL)).hasSize(100);
        assertThat(universe.stocks(Segment.OTHER)).hasSize(200); // rest of Nifty 500, placeholder row skipped
        assertThat(universe.symbols()).hasSize(500).doesNotHaveDuplicates().doesNotContain("DUMMYHEG");
        assertThat(universe.segmentOf("RELIANCE")).isEqualTo(Segment.LARGE);
        assertThat(universe.find("RELIANCE").orElseThrow().industry()).isNotBlank();
    }

    private static IndexMember member(String symbol) {
        return new IndexMember(symbol, symbol + " Ltd.", "Industry");
    }
}
