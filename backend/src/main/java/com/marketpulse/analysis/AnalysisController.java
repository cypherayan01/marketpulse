package com.marketpulse.analysis;

import com.marketpulse.config.MarketPulseProperties;
import com.marketpulse.jobs.Tokens;
import com.marketpulse.signals.TradeStyle;
import com.marketpulse.universe.Stock;
import com.marketpulse.universe.Universe;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** "Analyse a stock": buy / wait / avoid for any NSE stock, and keep / exit if you own it. */
@RestController
@RequestMapping("/api")
public class AnalysisController {

    private static final Pattern SYMBOL = Pattern.compile("[A-Za-z0-9&-]{1,20}");

    private final StockAnalyzer analyzer;
    private final StockExplainer explainer;
    private final Universe universe;
    private final String dashboardToken;

    public AnalysisController(StockAnalyzer analyzer, StockExplainer explainer, Universe universe,
                              MarketPulseProperties props) {
        this.analyzer = analyzer;
        this.explainer = explainer;
        this.universe = universe;
        this.dashboardToken = props.dashboard().token();
    }

    public record UniverseEntry(String symbol, String name, String segment) {
    }

    @GetMapping("/universe")
    public ResponseEntity<List<UniverseEntry>> universe(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (!authorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<UniverseEntry> entries = universe.symbols().stream()
                .map(s -> universe.find(s).orElseThrow())
                .map((Stock s) -> new UniverseEntry(s.symbol(), s.name(), s.segment().name()))
                .toList();
        return ResponseEntity.ok(entries);
    }

    @GetMapping("/analyze/{symbol}")
    public ResponseEntity<?> analyze(@RequestHeader(value = "Authorization", required = false) String auth,
                                     @PathVariable String symbol,
                                     @RequestParam(required = false) Double buyPrice,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate buyDate,
                                     @RequestParam(defaultValue = "1") int quantity,
                                     @RequestParam(defaultValue = "SWING") TradeStyle horizon,
                                     @RequestParam(required = false) Double stopLoss) {
        return run(auth, symbol, buyPrice, buyDate, quantity, horizon, stopLoss, false);
    }

    /** Same analysis, explained in plain English by Claude (one API call per request). */
    @PostMapping("/analyze/{symbol}/explain")
    public ResponseEntity<?> explain(@RequestHeader(value = "Authorization", required = false) String auth,
                                     @PathVariable String symbol,
                                     @RequestParam(required = false) Double buyPrice,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate buyDate,
                                     @RequestParam(defaultValue = "1") int quantity,
                                     @RequestParam(defaultValue = "SWING") TradeStyle horizon,
                                     @RequestParam(required = false) Double stopLoss) {
        return run(auth, symbol, buyPrice, buyDate, quantity, horizon, stopLoss, true);
    }

    private ResponseEntity<?> run(String auth, String symbol, Double buyPrice, LocalDate buyDate, int quantity,
                                  TradeStyle horizon, Double stopLoss, boolean explain) {
        if (!authorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!SYMBOL.matcher(symbol).matches()) {
            return error(HttpStatus.BAD_REQUEST, "symbol must be an NSE symbol like TCS or M&M");
        }
        Optional<OwnedPosition> owned = Optional.empty();
        if (buyPrice != null) {
            if (buyDate == null) {
                return error(HttpStatus.BAD_REQUEST, "buyDate is needed with buyPrice");
            }
            if (buyPrice <= 0 || quantity <= 0) {
                return error(HttpStatus.BAD_REQUEST, "buyPrice and quantity must be greater than 0");
            }
            TradeStyle h = horizon == TradeStyle.BTST ? TradeStyle.SWING : horizon;
            owned = Optional.of(new OwnedPosition(quantity, buyPrice, buyDate, h, stopLoss));
        }
        StockAnalysis analysis;
        try {
            analysis = analyzer.analyze(symbol.toUpperCase(Locale.ROOT), owned);
        } catch (UnknownStockException e) {
            return error(HttpStatus.NOT_FOUND, e.getMessage());
        }
        if (!explain) {
            return ResponseEntity.ok(analysis);
        }
        try {
            return ResponseEntity.ok(Map.of("text", explainer.explain(analysis)));
        } catch (RuntimeException e) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "AI explanation unavailable: " + e.getMessage());
        }
    }

    private boolean authorized(String auth) {
        return Tokens.matches(dashboardToken, Tokens.bearer(auth));
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
