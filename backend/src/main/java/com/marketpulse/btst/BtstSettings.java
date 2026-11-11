package com.marketpulse.btst;

import java.util.List;

public record BtstSettings(List<String> universe, String indexSymbol, int topN) {
}
