package com.example.claims.api.dto;

import com.example.claims.domain.ClaimEvent;
import com.example.claims.domain.ClaimStatus;
import java.time.Instant;

public record ClaimEventResponse(ClaimStatus fromStatus, ClaimStatus toStatus, String actor, String note, Instant at) {
    public static ClaimEventResponse from(ClaimEvent e) {
        return new ClaimEventResponse(e.getFromStatus(), e.getToStatus(), e.getActor(), e.getNote(), e.getCreatedAt());
    }
}
