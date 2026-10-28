package com.example.claims.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Claim lifecycle. The allowed transitions are the single source of truth for the state machine:
 * <pre>
 * SUBMITTED -> UNDER_REVIEW (on assignment)
 * UNDER_REVIEW -> APPROVED | REJECTED | MORE_INFO_REQUIRED
 * MORE_INFO_REQUIRED -> UNDER_REVIEW (claimant supplied information) | REJECTED (no response)
 * APPROVED -> SETTLED
 * </pre>
 */
public enum ClaimStatus {
    SUBMITTED, UNDER_REVIEW, MORE_INFO_REQUIRED, APPROVED, REJECTED, SETTLED;

    private static final Map<ClaimStatus, Set<ClaimStatus>> ALLOWED = new EnumMap<>(ClaimStatus.class);
    static {
        ALLOWED.put(SUBMITTED, Set.of(UNDER_REVIEW));
        ALLOWED.put(UNDER_REVIEW, Set.of(APPROVED, REJECTED, MORE_INFO_REQUIRED));
        ALLOWED.put(MORE_INFO_REQUIRED, Set.of(UNDER_REVIEW, REJECTED));
        ALLOWED.put(APPROVED, Set.of(SETTLED));
        ALLOWED.put(REJECTED, Set.of());
        ALLOWED.put(SETTLED, Set.of());
    }

    public boolean canTransitionTo(ClaimStatus next) {
        return ALLOWED.get(this).contains(next);
    }

    /** Claims that can still be (re)assigned to an officer. */
    public boolean isAssignable() {
        return this == SUBMITTED || this == UNDER_REVIEW || this == MORE_INFO_REQUIRED;
    }
}
