package com.example.claims.domain;

import org.junit.jupiter.api.Test;
import static com.example.claims.domain.ClaimStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class ClaimStatusTest {

    @Test
    void allowedTransitions() {
        assertTrue(SUBMITTED.canTransitionTo(UNDER_REVIEW));
        assertTrue(UNDER_REVIEW.canTransitionTo(APPROVED));
        assertTrue(UNDER_REVIEW.canTransitionTo(REJECTED));
        assertTrue(UNDER_REVIEW.canTransitionTo(MORE_INFO_REQUIRED));
        assertTrue(MORE_INFO_REQUIRED.canTransitionTo(UNDER_REVIEW));
        assertTrue(APPROVED.canTransitionTo(SETTLED));
    }

    @Test
    void forbiddenTransitions() {
        assertFalse(SUBMITTED.canTransitionTo(APPROVED));          // must be reviewed first
        assertFalse(MORE_INFO_REQUIRED.canTransitionTo(APPROVED)); // claimant must answer first
        assertFalse(UNDER_REVIEW.canTransitionTo(SETTLED));        // must be approved first
        assertFalse(APPROVED.canTransitionTo(REJECTED));
        for (ClaimStatus next : values()) {
            assertFalse(REJECTED.canTransitionTo(next));
            assertFalse(SETTLED.canTransitionTo(next));
        }
    }

    @Test
    void claimRefusesIllegalMove() {
        Claim claim = new Claim("CLM-X", new Claimant("A", "a@x.com", "1"), "India", "Motor",
            java.math.BigDecimal.TEN, "d");
        assertThrows(InvalidStateTransitionException.class, () -> claim.moveTo(SETTLED));
        assertEquals(SUBMITTED, claim.getStatus());
    }
}
