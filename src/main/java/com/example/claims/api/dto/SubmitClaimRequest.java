package com.example.claims.api.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Locale;

public record SubmitClaimRequest(
    @NotBlank @Size(max=120) String claimantName,
    @NotBlank @Email String claimantEmail,
    @NotBlank @Size(max=40) String claimantPhone,
    @NotBlank @Size(max=80) String market,
    @NotBlank @Size(max=80) String claimType,
    @NotNull @DecimalMin(value="0.01") @Digits(integer=12, fraction=2) BigDecimal claimedAmount,
    @Size(max=2000) String description
) {
    public SubmitClaimRequest {
        claimantEmail = claimantEmail == null ? null : claimantEmail.trim().toLowerCase(Locale.ROOT);
    }
}
