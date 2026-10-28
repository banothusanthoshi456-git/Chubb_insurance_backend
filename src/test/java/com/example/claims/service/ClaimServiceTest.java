package com.example.claims.service;

import com.example.claims.api.dto.*;
import com.example.claims.domain.*;
import com.example.claims.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClaimServiceTest {
    @Mock ClaimRepository claims;
    @Mock ClaimantRepository claimants;
    @Mock ClaimsStaffRepository staff;
    @Mock ClaimEventRepository events;
    ClaimService service;

    ClaimsStaff asha, rahul;
    Claim claim;

    @BeforeEach
    void setUp() {
        service = new ClaimService(claims, claimants, staff, events, 2);
        asha = officer(1L, "Asha", true);
        rahul = officer(2L, "Rahul", true);
        claim = new Claim("CLM-1", new Claimant("Priya", "priya@example.com", "1"), "India", "Motor",
            new BigDecimal("1000.00"), "dent");
        ReflectionTestUtils.setField(claim, "id", 10L);
        when(claims.findById(10L)).thenReturn(Optional.of(claim));
        when(claims.saveAndFlush(any(Claim.class))).thenAnswer(i -> i.getArgument(0));
        when(staff.findById(1L)).thenReturn(Optional.of(asha));
        when(staff.findById(2L)).thenReturn(Optional.of(rahul));
    }

    private static ClaimsStaff officer(Long id, String name, boolean active) {
        ClaimsStaff s = new ClaimsStaff(name, name.toLowerCase() + "@example.com");
        ReflectionTestUtils.setField(s, "id", id);
        s.setActive(active);
        return s;
    }

    private int statusOf(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run).getStatusCode().value();
    }

    // ---- submit ----

    @Test
    void submitReusesExistingClaimantByNormalisedEmail() {
        Claimant existing = new Claimant("Priya", "priya@example.com", "1");
        when(claimants.findByEmailIgnoreCase("priya@example.com")).thenReturn(Optional.of(existing));
        ClaimResponse r = service.submit(new SubmitClaimRequest("Priya", "  PRIYA@Example.com ", "1",
            "India", "Motor", new BigDecimal("50.00"), "x"));
        verify(claimants, never()).save(any());
        verify(claimants).findByEmailIgnoreCase("priya@example.com");
        assertEquals(ClaimStatus.SUBMITTED, r.status());
        assertTrue(r.claimNumber().startsWith("CLM-"));
        verify(events).save(any(ClaimEvent.class));
    }

    @Test
    void submitCreatesClaimantWhenNew() {
        when(claimants.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(claimants.save(any(Claimant.class))).thenAnswer(i -> i.getArgument(0));
        service.submit(new SubmitClaimRequest("New", "new@example.com", "1", "Japan", "Property",
            new BigDecimal("50.00"), null));
        verify(claimants).save(argThat(claimant -> "new@example.com".equals(claimant.getEmail())));
    }

    @Test
    void submitRejectsAnExistingClaimWithTheSameDetailsBeforeCreatingClaimant() {
        when(claims.findDuplicateSubmissionIds(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(1L));
        SubmitClaimRequest request = new SubmitClaimRequest("Priya", "priya@example.com", "1",
                "India", "Motor", new BigDecimal("50.00"), "x");

        assertEquals(409, statusOf(() -> service.submit(request)));
        verify(claimants, never()).save(any());
        verify(claims, never()).saveAndFlush(any(Claim.class));
        verify(events, never()).save(any(ClaimEvent.class));
    }

    // ---- assign ----

    @Test
    void assignMovesSubmittedToUnderReview() {
        ClaimResponse r = service.assign(10L, new AssignClaimRequest(1L));
        assertEquals(ClaimStatus.UNDER_REVIEW, r.status());
        assertEquals(1L, r.assignedStaffId());
    }

    @Test
    void assignRejectsInactiveAndMissingStaff() {
        ClaimsStaff gone = officer(3L, "Gone", false);
        when(staff.findById(3L)).thenReturn(Optional.of(gone));
        assertEquals(400, statusOf(() -> service.assign(10L, new AssignClaimRequest(3L))));
        assertEquals(404, statusOf(() -> service.assign(10L, new AssignClaimRequest(99L))));
        assertEquals(404, statusOf(() -> service.assign(999L, new AssignClaimRequest(1L))));
    }

    @Test
    void assignRefusesWhenOfficerAtCapacity() {
        when(claims.countByAssignedStaff_IdAndStatusIn(eq(1L), any())).thenReturn(2L);
        assertEquals(409, statusOf(() -> service.assign(10L, new AssignClaimRequest(1L))));
    }

    @Test
    void assignToSameOfficerIsIdempotentEvenAtCapacity() {
        service.assign(10L, new AssignClaimRequest(1L));
        when(claims.countByAssignedStaff_IdAndStatusIn(eq(1L), any())).thenReturn(2L);
        assertDoesNotThrow(() -> service.assign(10L, new AssignClaimRequest(1L)));
    }

    @Test
    void closedClaimCannotBeReassigned() {
        service.assign(10L, new AssignClaimRequest(1L));
        service.decide(10L, new DecisionRequest(1L, DecisionRequest.Decision.REJECT, null, "no cover"));
        assertEquals(409, statusOf(() -> service.assign(10L, new AssignClaimRequest(2L))));
    }

    // ---- decide ----

    @Test
    void decisionRequiresAssignment() {
        assertEquals(409, statusOf(() ->
            service.decide(10L, new DecisionRequest(1L, DecisionRequest.Decision.REJECT, null, "n"))));
    }

    @Test
    void onlyAssignedOfficerMayDecide() {
        service.assign(10L, new AssignClaimRequest(1L));
        assertEquals(403, statusOf(() ->
            service.decide(10L, new DecisionRequest(2L, DecisionRequest.Decision.REJECT, null, "n"))));
    }

    @Test
    void approveValidatesAmount() {
        service.assign(10L, new AssignClaimRequest(1L));
        assertEquals(400, statusOf(() -> service.decide(10L,
            new DecisionRequest(1L, DecisionRequest.Decision.APPROVE, null, "n"))));
        assertEquals(400, statusOf(() -> service.decide(10L,
            new DecisionRequest(1L, DecisionRequest.Decision.APPROVE, BigDecimal.ZERO, "n"))));
        assertEquals(400, statusOf(() -> service.decide(10L,
            new DecisionRequest(1L, DecisionRequest.Decision.APPROVE, new BigDecimal("1000.01"), "n"))));
        assertEquals(ClaimStatus.UNDER_REVIEW, claim.getStatus());
    }

    @Test
    void approveThenSettle() {
        service.assign(10L, new AssignClaimRequest(1L));
        ClaimResponse approved = service.decide(10L,
            new DecisionRequest(1L, DecisionRequest.Decision.APPROVE, new BigDecimal("800.00"), "ok"));
        assertEquals(ClaimStatus.APPROVED, approved.status());
        assertEquals(new BigDecimal("800.00"), approved.approvedAmount());
        assertEquals(ClaimStatus.SETTLED, service.decide(10L,
            new DecisionRequest(1L, DecisionRequest.Decision.SETTLE, null, "paid")).status());
    }

    @Test
    void cannotSettleBeforeApproval() {
        service.assign(10L, new AssignClaimRequest(1L));
        assertThrows(InvalidStateTransitionException.class, () -> service.decide(10L,
            new DecisionRequest(1L, DecisionRequest.Decision.SETTLE, null, "n")));
    }

    @Test
    void noDecisionAfterTerminalState() {
        service.assign(10L, new AssignClaimRequest(1L));
        service.decide(10L, new DecisionRequest(1L, DecisionRequest.Decision.REJECT, null, "n"));
        assertThrows(InvalidStateTransitionException.class, () -> service.decide(10L,
            new DecisionRequest(1L, DecisionRequest.Decision.APPROVE, new BigDecimal("1.00"), "n")));
    }

    // ---- more information loop ----

    @Test
    void moreInfoLoopReturnsClaimToReview() {
        service.assign(10L, new AssignClaimRequest(1L));
        service.decide(10L, new DecisionRequest(1L, DecisionRequest.Decision.REQUEST_MORE_INFO, null, "need photos"));
        assertEquals(ClaimStatus.MORE_INFO_REQUIRED, claim.getStatus());
        // cannot approve while waiting for the claimant
        assertThrows(InvalidStateTransitionException.class, () -> service.decide(10L,
            new DecisionRequest(1L, DecisionRequest.Decision.APPROVE, new BigDecimal("1.00"), "n")));
        ClaimResponse r = service.provideInformation(10L,
            new ProvideInformationRequest("PRIYA@example.com", "photos attached"));
        assertEquals(ClaimStatus.UNDER_REVIEW, r.status());
    }

    @Test
    void provideInformationChecksIdentityAndState() {
        assertEquals(409, statusOf(() -> service.provideInformation(10L,
            new ProvideInformationRequest("priya@example.com", "x"))));           // nothing requested
        service.assign(10L, new AssignClaimRequest(1L));
        service.decide(10L, new DecisionRequest(1L, DecisionRequest.Decision.REQUEST_MORE_INFO, null, "n"));
        assertEquals(403, statusOf(() -> service.provideInformation(10L,
            new ProvideInformationRequest("someone-else@example.com", "x"))));    // wrong claimant
    }

    // ---- list ----

    @Test
    void listValidatesPagingAndFilterCombination() {
        assertEquals(400, statusOf(() -> service.list(null, 1L, true, 0, 20)));
        assertEquals(400, statusOf(() -> service.list(null, null, false, -1, 20)));
        assertEquals(400, statusOf(() -> service.list(null, null, false, 0, 1000)));
    }
}
