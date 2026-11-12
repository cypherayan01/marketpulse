package com.marketpulse.predictions;

import com.marketpulse.config.MarketPulseProperties;
import com.marketpulse.jobs.Tokens;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** How the predictions turned out: the scorecard, and what was predicted on a given day. */
@RestController
@RequestMapping("/api/predictions")
public class PredictionsController {

    static final String REPLAY_CAVEAT = "Replayed from history: an estimate. It uses today's index lists (stocks that "
            + "left the indices are missing, which flatters results), no news, and BTST without last-hour data. "
            + "Live predictions are the real test.";

    private final PredictionRepository repository;
    private final ScorecardCalculator scorecard = new ScorecardCalculator();
    private final String dashboardToken;

    public PredictionsController(PredictionRepository repository, MarketPulseProperties props) {
        this.repository = repository;
        this.dashboardToken = props.dashboard().token();
    }

    @GetMapping("/scorecard")
    public ResponseEntity<ScorecardView> scorecard(@RequestHeader(value = "Authorization", required = false) String auth,
                                                   @RequestParam(defaultValue = "LIVE") PredictionSource source) {
        if (!authorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<Prediction> all = repository.findAll(source);
        LocalDate first = all.stream().map(Prediction::signalDate).min(Comparator.naturalOrder()).orElse(null);
        LocalDate last = all.stream().map(Prediction::signalDate).max(Comparator.naturalOrder()).orElse(null);
        return ResponseEntity.ok(new ScorecardView(source, all.size(), first, last, scorecard.summarize(all),
                source == PredictionSource.REPLAY ? REPLAY_CAVEAT : null));
    }

    @GetMapping
    public ResponseEntity<List<Prediction>> onDay(@RequestHeader(value = "Authorization", required = false) String auth,
                                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                  @RequestParam(defaultValue = "LIVE") PredictionSource source) {
        if (!authorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(repository.findByDate(source, date));
    }

    @GetMapping("/dates")
    public ResponseEntity<List<LocalDate>> dates(@RequestHeader(value = "Authorization", required = false) String auth,
                                                 @RequestParam(defaultValue = "LIVE") PredictionSource source) {
        if (!authorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(repository.findDates(source));
    }

    private boolean authorized(String auth) {
        return Tokens.matches(dashboardToken, Tokens.bearer(auth));
    }
}
