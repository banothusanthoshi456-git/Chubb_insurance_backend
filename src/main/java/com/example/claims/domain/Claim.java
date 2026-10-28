package com.example.claims.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

@Entity
@Table(name = "claims", indexes = {
    @Index(name = "idx_claim_status", columnList = "status"),
    @Index(name = "idx_claim_staff_status", columnList = "assigned_staff_id,status"),
    @Index(name = "idx_claim_created", columnList = "createdAt")
}, uniqueConstraints = @UniqueConstraint(name = "uk_claims_submission_fingerprint",
        columnNames = "submission_fingerprint"))
public class Claim {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** Optimistic lock: a concurrent update to the same claim fails instead of silently overwriting. */
    @Version
    private Long version;
    @Column(nullable = false, unique = true, length = 30)
    private String claimNumber;
    @Column(name = "submission_fingerprint", length = 64)
    private String submissionFingerprint;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Claimant claimant;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_staff_id")
    private ClaimsStaff assignedStaff;
    @Column(nullable = false, length = 80)
    private String market;
    @Column(nullable = false, length = 80)
    private String claimType;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal claimedAmount;
    @Column(precision = 14, scale = 2)
    private BigDecimal approvedAmount;
    @Column(length = 2000)
    private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private ClaimStatus status = ClaimStatus.SUBMITTED;
    @Column(length = 2000)
    private String decisionNotes;
    /** Minutes from submission to approve/reject; used for officer performance reporting. */
    private Long resolutionMinutes;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected Claim() {}
    public Claim(String claimNumber, Claimant claimant, String market, String claimType,
                 BigDecimal claimedAmount, String description) {
        this.claimNumber = claimNumber; this.claimant = claimant; this.market = market;
        this.claimType = claimType; this.claimedAmount = claimedAmount; this.description = description;
        this.status = ClaimStatus.SUBMITTED; this.createdAt = Instant.now(); this.updatedAt = Instant.now();
    }
    @PrePersist void prepareForInsert() {
        updatedAt = Instant.now();
        submissionFingerprint = fingerprint(claimant.getEmail(), market, claimType, claimedAmount, description);
    }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    private static String fingerprint(String email, String market, String claimType,
                                      BigDecimal claimedAmount, String description) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String amount = claimedAmount.setScale(2).toPlainString();
        String canonical = field(normalizedEmail) + field(market) + field(claimType)
                + field(amount) + field(description);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static String field(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    public Long getId() { return id; }
    public String getClaimNumber() { return claimNumber; }
    public Claimant getClaimant() { return claimant; }
    public ClaimsStaff getAssignedStaff() { return assignedStaff; }
    public String getMarket() { return market; }
    public String getClaimType() { return claimType; }
    public BigDecimal getClaimedAmount() { return claimedAmount; }
    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public String getDescription() { return description; }
    public ClaimStatus getStatus() { return status; }
    public String getDecisionNotes() { return decisionNotes; }
    public Long getResolutionMinutes() { return resolutionMinutes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void assign(ClaimsStaff staff) { this.assignedStaff = staff; }

    public void requireCanMoveTo(ClaimStatus next) {
        if (!status.canTransitionTo(next)) throw new InvalidStateTransitionException(status, next);
    }

    /** The only way to change status; enforces the transition table. */
    public void moveTo(ClaimStatus next) {
        requireCanMoveTo(next);
        this.status = next;
        this.updatedAt = Instant.now();
    }

    public void decide(ClaimStatus next, BigDecimal amount, String notes) {
        moveTo(next);
        this.approvedAmount = amount;
        this.decisionNotes = notes;
        if (next == ClaimStatus.APPROVED || next == ClaimStatus.REJECTED) {
            this.resolutionMinutes = Duration.between(createdAt, Instant.now()).toMinutes();
        }
    }

    public void setDecisionNotes(String notes) { this.decisionNotes = notes; }
}
