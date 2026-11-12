package com.marketpulse.universe;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads niftyindices.com constituent CSVs ("Company Name,Industry,Symbol,Series,ISIN Code").
 * Placeholder rows NSE adds during corporate actions (symbol starting with DUMMY) are skipped.
 */
public final class IndexListParser {

    private IndexListParser() {
    }

    public static List<IndexMember> parse(InputStream csv) {
        List<IndexMember> members = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csv, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                return members;
            }
            String line;
            while ((line = reader.readLine()) != null) {
                String[] cols = line.split(",", -1);
                if (cols.length < 3 || cols[2].isBlank() || cols[2].trim().toUpperCase().startsWith("DUMMY")) {
                    continue;
                }
                members.add(new IndexMember(cols[2].trim(), cols[0].trim(), cols[1].trim()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return members;
    }
}
