package com.example.claims.api.dto;

import com.example.claims.domain.Claim;
import com.example.claims.domain.ClaimStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record ClaimResponse(
    Long id, String claimNumber, Long claimantId, String claimantName, String claimantEmail,
    Long assignedStaffId, String assignedStaffName, String market, String claimType,
    BigDecimal claimedAmount, BigDecimal approvedAmount, String description,
    ClaimStatus status, String decisionNotes, Instant createdAt, Instant updatedAt
) {
    public static ClaimResponse from(Claim c) {
        return new ClaimResponse(c.getId(), c.getClaimNumber(), c.getClaimant().getId(),
            c.getClaimant().getFullName(), c.getClaimant().getEmail(),
            c.getAssignedStaff() == null ? null : c.getAssignedStaff().getId(),
            c.getAssignedStaff() == null ? null : c.getAssignedStaff().getFullName(),
            c.getMarket(), c.getClaimType(), c.getClaimedAmount(), c.getApprovedAmount(),
            c.getDescription(), c.getStatus(), c.getDecisionNotes(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
