package com.marketpulse.holdings;

import com.marketpulse.signals.TradeStyle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/** What the dashboard sends to add a holding. */
public record HoldingRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9&-]{1,20}", message = "must be an NSE symbol like TCS or M&M") String symbol,
        @NotNull @Positive Integer quantity,
        @NotNull @Positive Double buyPrice,
        @NotNull LocalDate buyDate, // "not in the future" is checked in IST by the controller
        @NotNull TradeStyle horizon,
        @Positive Double stopLoss,
        @Positive Double target) {
}
