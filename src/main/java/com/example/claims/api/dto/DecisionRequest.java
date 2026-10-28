package com.example.claims.api.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * staffId identifies the acting officer (stand-in for the authenticated principal until security is added);
 * only the officer the claim is assigned to may record a decision.
 */
public record DecisionRequest(
    @NotNull Long staffId,
    @NotNull Decision decision,
    @DecimalMin(value="0.00") @Digits(integer=12, fraction=2) BigDecimal approvedAmount,
    @NotBlank @Size(max=2000) String notes
) {
    public enum Decision { APPROVE, REJECT, REQUEST_MORE_INFO, SETTLE }
}
