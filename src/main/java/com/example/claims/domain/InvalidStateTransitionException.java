package com.example.claims.domain;

public class InvalidStateTransitionException extends RuntimeException {
    public InvalidStateTransitionException(ClaimStatus from, ClaimStatus to) {
        super("Claim cannot move from " + from + " to " + to);
    }
}
