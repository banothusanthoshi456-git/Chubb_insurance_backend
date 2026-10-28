package com.example.claims.api.dto;

import jakarta.validation.constraints.*;

/** Claimant's reply to a "more information required" request. The email must match the claim's claimant. */
public record ProvideInformationRequest(
    @NotBlank @Email String claimantEmail,
    @NotBlank @Size(max=2000) String message
) {}
