package com.marketpulse.btst;

import com.marketpulse.config.MarketPulseProperties;
import com.marketpulse.jobs.Tokens;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Dashboard API for the BTST price range. */
@RestController
@RequestMapping("/api/settings/btst")
public class BtstSettingsController {

    private final BtstPreferences preferences;
    private final String dashboardToken;

    public BtstSettingsController(BtstPreferences preferences, MarketPulseProperties props) {
        this.preferences = preferences;
        this.dashboardToken = props.dashboard().token();
    }

    public record PriceRangeView(Double minPrice, Double maxPrice, String label) {
        static PriceRangeView of(PriceRange range) {
            return new PriceRangeView(range.minPrice(), range.maxPrice(), range.label());
        }
    }

    public record PriceRangeRequest(Double minPrice, Double maxPrice) {
    }

    @GetMapping
    public ResponseEntity<PriceRangeView> get(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (!authorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(PriceRangeView.of(preferences.priceRange()));
    }

    @PutMapping
    public ResponseEntity<?> save(@RequestHeader(value = "Authorization", required = false) String auth,
                                  @RequestBody PriceRangeRequest request) {
        if (!authorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        PriceRange range;
        try {
            range = new PriceRange(request.minPrice(), request.maxPrice());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        preferences.savePriceRange(range);
        return ResponseEntity.ok(PriceRangeView.of(range));
    }

    private boolean authorized(String auth) {
        return Tokens.matches(dashboardToken, Tokens.bearer(auth));
    }
}
