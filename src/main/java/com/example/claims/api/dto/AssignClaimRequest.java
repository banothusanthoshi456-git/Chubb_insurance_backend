package com.example.claims.api.dto;
import jakarta.validation.constraints.NotNull;
public record AssignClaimRequest(@NotNull Long staffId) {}
