package com.example.claims.api.dto;

import com.example.claims.domain.ClaimsStaff;
import jakarta.validation.constraints.*;
import java.util.Map;

public final class StaffDtos {
    private StaffDtos() {}

    public record CreateStaffRequest(@NotBlank @Size(max=120) String fullName, @NotBlank @Email String email) {}

    public record SetActiveRequest(@NotNull Boolean active) {}

    public record StaffResponse(Long id, String fullName, String email, boolean active) {
        public static StaffResponse from(ClaimsStaff s) {
            return new StaffResponse(s.getId(), s.getFullName(), s.getEmail(), s.isActive());
        }
    }

    /** openClaims = SUBMITTED + UNDER_REVIEW + MORE_INFO_REQUIRED; avgMinutesToDecision is null until a decision exists. */
    public record WorkloadResponse(Long staffId, String fullName, boolean active, long openClaims,
                                   Map<String, Long> claimsByStatus, Double avgMinutesToDecision) {}
}
