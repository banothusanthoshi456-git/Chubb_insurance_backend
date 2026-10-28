package com.example.claims.service;

import com.example.claims.api.dto.*;
import com.example.claims.domain.*;
import com.example.claims.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;

@Service
public class ClaimService {
    static final List<ClaimStatus> OPEN_STATUSES =
            List.of(ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW, ClaimStatus.MORE_INFO_REQUIRED);
    static final int MAX_PAGE_SIZE = 100;

    private final ClaimRepository claims;
    private final ClaimantRepository claimants;
    private final ClaimsStaffRepository staff;
    private final ClaimEventRepository events;
    private final int maxOpenPerOfficer;

    public ClaimService(ClaimRepository claims, ClaimantRepository claimants, ClaimsStaffRepository staff,
                        ClaimEventRepository events,
                        @Value("${claims.max-open-per-officer:25}") int maxOpenPerOfficer) {
        this.claims = claims; this.claimants = claimants; this.staff = staff;
        this.events = events; this.maxOpenPerOfficer = maxOpenPerOfficer;
    }

    @Transactional
    public ClaimResponse submit(SubmitClaimRequest req) {
        String email = req.claimantEmail().trim().toLowerCase(Locale.ROOT);
        if (!claims.findDuplicateSubmissionIds(email, req.market(), req.claimType(),
                req.claimedAmount(), req.description(), PageRequest.of(0, 1)).isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A claim with the same claimant email and details already exists.");
        Claimant claimant = claimants.findByEmailIgnoreCase(email)
                .orElseGet(() -> claimants.save(new Claimant(req.claimantName(), email, req.claimantPhone())));
        String number = "CLM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        Claim saved = claims.saveAndFlush(new Claim(number, claimant, req.market(), req.claimType(),
                req.claimedAmount(), req.description()));
        events.save(new ClaimEvent(saved.getId(), null, ClaimStatus.SUBMITTED, "claimant:" + email, "Claim submitted"));
        return ClaimResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ClaimResponse get(Long id) { return ClaimResponse.from(requireClaim(id)); }

    @Transactional(readOnly = true)
    public List<ClaimResponse> claimsForClaimant(Long claimantId) {
        if (!claimants.existsById(claimantId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Claimant not found");
        return claims.findByClaimant_IdOrderByCreatedAtDescIdDesc(claimantId).stream()
                .map(ClaimResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClaimResponse getByNumber(String claimNumber) {
        return claims.findByClaimNumber(claimNumber.toUpperCase())
                .map(ClaimResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));
    }

    @Transactional(readOnly = true)
    public List<ClaimEventResponse> history(Long id) {
        requireClaim(id);
        return events.findByClaimIdOrderByCreatedAtAscIdAsc(id).stream().map(ClaimEventResponse::from).toList();
    }

    /** Filters combine (AND). unassigned=true gives the queue of claims waiting to be picked up. */
    @Transactional(readOnly = true)
    public PagedResponse<ClaimResponse> list(ClaimStatus status, Long staffId, boolean unassigned, int page, int size) {
        if (unassigned && staffId != null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use either staffId or unassigned, not both");
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE);
        Specification<Claim> spec = Specification.where(null);
        if (status != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        if (staffId != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("assignedStaff").get("id"), staffId));
        if (unassigned) spec = spec.and((r, q, cb) -> cb.isNull(r.get("assignedStaff")));
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        return PagedResponse.of(claims.findAll(spec, pageable), ClaimResponse::from);
    }

    /** Assign (or pick up / reassign). Idempotent for the same officer. */
    @Transactional
    public ClaimResponse assign(Long claimId, AssignClaimRequest req) {
        Claim claim = requireClaim(claimId);
        ClaimsStaff officer = requireStaff(req.staffId());
        if (!officer.isActive()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Staff member is inactive");
        if (!claim.getStatus().isAssignable())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Closed claims cannot be reassigned");
        boolean alreadyTheirs = claim.getAssignedStaff() != null && claim.getAssignedStaff().getId().equals(officer.getId());
        if (!alreadyTheirs && claims.countByAssignedStaff_IdAndStatusIn(officer.getId(), OPEN_STATUSES) >= maxOpenPerOfficer)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Officer already has the maximum of " + maxOpenPerOfficer + " open claims");
        ClaimStatus before = claim.getStatus();
        claim.assign(officer);
        if (before == ClaimStatus.SUBMITTED) claim.moveTo(ClaimStatus.UNDER_REVIEW);
        Claim saved = claims.saveAndFlush(claim);
        if (!alreadyTheirs)
            events.save(new ClaimEvent(saved.getId(), before, saved.getStatus(),
                    "staff:" + officer.getId(), "Assigned to " + officer.getFullName()));
        return ClaimResponse.from(saved);
    }

    @Transactional
    public ClaimResponse decide(Long id, DecisionRequest req) {
        Claim claim = requireClaim(id);
        ClaimsStaff officer = requireStaff(req.staffId());
        if (claim.getAssignedStaff() == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assign a claims officer before reviewing");
        if (!claim.getAssignedStaff().getId().equals(officer.getId()) || !officer.isActive())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assigned, active officer can record a decision");

        ClaimStatus before = claim.getStatus();
        ClaimStatus target = switch (req.decision()) {
            case APPROVE -> ClaimStatus.APPROVED;
            case REJECT -> ClaimStatus.REJECTED;
            case REQUEST_MORE_INFO -> ClaimStatus.MORE_INFO_REQUIRED;
            case SETTLE -> ClaimStatus.SETTLED;
        };
        claim.requireCanMoveTo(target);   // 409 before any field validation, so the state error wins

        switch (req.decision()) {
            case APPROVE -> {
                BigDecimal amount = req.approvedAmount();
                if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A positive approvedAmount is required");
                if (amount.compareTo(claim.getClaimedAmount()) > 0)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Approved amount cannot exceed claimed amount");
                claim.decide(target, amount, req.notes());
            }
            case REJECT -> claim.decide(target, BigDecimal.ZERO, req.notes());
            case REQUEST_MORE_INFO -> { claim.moveTo(target); claim.setDecisionNotes(req.notes()); }
            case SETTLE -> { claim.moveTo(target); claim.setDecisionNotes(req.notes()); }
        }
        Claim saved = claims.saveAndFlush(claim);
        events.save(new ClaimEvent(saved.getId(), before, target, "staff:" + officer.getId(),
                req.decision() + ": " + req.notes()));
        return ClaimResponse.from(saved);
    }

    /** Claimant answers a "more information required" request; the claim goes back to review. */
    @Transactional
    public ClaimResponse provideInformation(Long id, ProvideInformationRequest req) {
        Claim claim = requireClaim(id);
        String email = req.claimantEmail().trim().toLowerCase();
        if (!claim.getClaimant().getEmail().equalsIgnoreCase(email))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Email does not match the claimant on this claim");
        ClaimStatus before = claim.getStatus();
        if (before != ClaimStatus.MORE_INFO_REQUIRED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No information has been requested for this claim");
        claim.moveTo(ClaimStatus.UNDER_REVIEW);
        Claim saved = claims.saveAndFlush(claim);
        events.save(new ClaimEvent(saved.getId(), before, ClaimStatus.UNDER_REVIEW, "claimant:" + email, req.message()));
        return ClaimResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ClaimStatus s : ClaimStatus.values()) byStatus.put(s.name(), 0L);
        long total = 0;
        for (Object[] row : claims.countByStatus()) {
            long n = (Long) row[1];
            byStatus.put(((ClaimStatus) row[0]).name(), n);
            total += n;
        }
        List<DashboardResponse.MarketExposure> markets = claims.exposureByMarket().stream()
                .map(r -> new DashboardResponse.MarketExposure((String) r[0], (Long) r[1], orZero((BigDecimal) r[2])))
                .toList();
        return new DashboardResponse(total, byStatus, orZero(claims.outstandingExposure()),
                orZero(claims.totalApprovedAmount()), markets);
    }

    private static BigDecimal orZero(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private Claim requireClaim(Long id) {
        return claims.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));
    }
    private ClaimsStaff requireStaff(Long id) {
        return staff.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claims staff not found"));
    }
}
