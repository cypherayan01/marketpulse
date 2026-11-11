package com.marketpulse.holdings;

import com.marketpulse.config.MarketPulseProperties;
import com.marketpulse.jobs.Tokens;
import com.marketpulse.market.MarketDataClient;
import com.marketpulse.reports.ReportStore;
import com.marketpulse.signals.TradeStyle;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Manage the stocks you own from the dashboard. Same {@code Authorization: Bearer <DASHBOARD_TOKEN>} as /api. */
@RestController
@RequestMapping("/api/holdings")
public class HoldingsController {

    /** Enough daily history for the indicators (about a year of weekly bars for positional). */
    private static final int MIN_HISTORY = 230;
    /** The server runs in UTC; "today" for an Indian investor is the IST date. */
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final HoldingRepository repository;
    private final MarketDataClient market;
    private final ReportStore store;
    private final Clock clock;
    private final String dashboardToken;

    public HoldingsController(HoldingRepository repository, MarketDataClient market, ReportStore store, Clock clock,
                              MarketPulseProperties props) {
        this.repository = repository;
        this.market = market;
        this.store = store;
        this.clock = clock;
        this.dashboardToken = props.dashboard().token();
    }

    @GetMapping
    public ResponseEntity<HoldingsView> list(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (!authorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(new HoldingsView(repository.findAll(), store.latestHoldings().orElse(null)));
    }

    @PostMapping
    public ResponseEntity<?> add(@RequestHeader(value = "Authorization", required = false) String auth,
                                 @Valid @RequestBody HoldingRequest request) {
        if (!authorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (request.buyDate().isAfter(LocalDate.now(clock.withZone(IST)))) {
            return error("buyDate can't be in the future");
        }
        if (request.horizon() == TradeStyle.BTST) {
            return error("horizon must be SWING or POSITIONAL - BTST positions are exited the next morning");
        }
        String symbol = request.symbol().trim().toUpperCase(Locale.ROOT);
        int history;
        try {
            history = market.dailyCandles(symbol).size();
        } catch (RuntimeException e) {
            return error("No NSE price data found for %s - check the symbol".formatted(symbol));
        }
        if (history < MIN_HISTORY) {
            return error("%s has too little price history to analyse (needs about a year)".formatted(symbol));
        }
        Holding saved = repository.add(new Holding(null, symbol, request.quantity(), request.buyPrice(),
                request.buyDate(), request.horizon(), request.stopLoss(), request.target(), clock.instant()));
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestHeader(value = "Authorization", required = false) String auth,
                                       @PathVariable long id) {
        if (!authorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return repository.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /** Binding and validation run before the method body, so check the token here too before revealing anything. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e, HttpServletRequest request) {
        if (!authorized(request.getHeader("Authorization"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> unreadable(HttpServletRequest request) {
        if (!authorized(request.getHeader("Authorization"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return error("Request body is not valid JSON for a holding");
    }

    private boolean authorized(String auth) {
        return Tokens.matches(dashboardToken, Tokens.bearer(auth));
    }

    private static ResponseEntity<Map<String, String>> error(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
