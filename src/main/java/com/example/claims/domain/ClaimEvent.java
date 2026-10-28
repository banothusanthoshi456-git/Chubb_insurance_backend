package com.example.claims.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Append-only audit / history record: who moved a claim from which status to which, and why. */
@Entity
@Table(name = "claim_events", indexes = @Index(name = "idx_event_claim", columnList = "claimId"))
public class ClaimEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long claimId;
    @Enumerated(EnumType.STRING) @Column(length = 30)
    private ClaimStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private ClaimStatus toStatus;
    @Column(nullable = false, length = 200)
    private String actor;
    @Column(length = 2000)
    private String note;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected ClaimEvent() {}
    public ClaimEvent(Long claimId, ClaimStatus fromStatus, ClaimStatus toStatus, String actor, String note) {
        this.claimId = claimId; this.fromStatus = fromStatus; this.toStatus = toStatus;
        this.actor = actor; this.note = note;
    }
    public Long getId() { return id; }
    public Long getClaimId() { return claimId; }
    public ClaimStatus getFromStatus() { return fromStatus; }
    public ClaimStatus getToStatus() { return toStatus; }
    public String getActor() { return actor; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
