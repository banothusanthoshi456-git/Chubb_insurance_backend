package com.example.claims.repository;

import com.example.claims.domain.Claim;
import com.example.claims.domain.ClaimStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ClaimRepository extends JpaRepository<Claim, Long>, JpaSpecificationExecutor<Claim> {

    @EntityGraph(attributePaths = {"claimant", "assignedStaff"})
    Optional<Claim> findByClaimNumber(String claimNumber);

    @EntityGraph(attributePaths = {"claimant", "assignedStaff"})
    List<Claim> findByClaimant_IdOrderByCreatedAtDescIdDesc(Long claimantId);

    @Query("""
        select c.id from Claim c
        where lower(trim(c.claimant.email)) = :email
          and c.market = :market
          and c.claimType = :claimType
          and c.claimedAmount = :claimedAmount
          and ((c.description is null and :description is null) or c.description = :description)
        """)
    List<Long> findDuplicateSubmissionIds(@Param("email") String email, @Param("market") String market,
                                          @Param("claimType") String claimType,
                                          @Param("claimedAmount") BigDecimal claimedAmount,
                                          @Param("description") String description, Pageable pageable);

    /** Fetches claimant and officer in the same query, so listing N claims is not N+1 selects. */
    @Override
    @EntityGraph(attributePaths = {"claimant", "assignedStaff"})
    Page<Claim> findAll(Specification<Claim> spec, Pageable pageable);

    long countByAssignedStaff_IdAndStatusIn(Long staffId, List<ClaimStatus> statuses);

    // ---- dashboard aggregates: computed by the database, not by loading every claim ----

    @Query("select c.status, count(c) from Claim c group by c.status")
    List<Object[]> countByStatus();

    /** Open exposure: claimed amount while under assessment, approved amount once approved (awaiting settlement). */
    @Query("""
        select sum(case when c.status = com.example.claims.domain.ClaimStatus.APPROVED
                        then c.approvedAmount else c.claimedAmount end)
        from Claim c
        where c.status not in (com.example.claims.domain.ClaimStatus.REJECTED,
                               com.example.claims.domain.ClaimStatus.SETTLED)
        """)
    BigDecimal outstandingExposure();

    @Query("""
        select sum(c.approvedAmount) from Claim c
        where c.status in (com.example.claims.domain.ClaimStatus.APPROVED,
                           com.example.claims.domain.ClaimStatus.SETTLED)
        """)
    BigDecimal totalApprovedAmount();

    @Query("""
        select c.market, count(c),
               sum(case when c.status = com.example.claims.domain.ClaimStatus.APPROVED
                        then c.approvedAmount else c.claimedAmount end)
        from Claim c
        where c.status not in (com.example.claims.domain.ClaimStatus.REJECTED,
                               com.example.claims.domain.ClaimStatus.SETTLED)
        group by c.market order by c.market
        """)
    List<Object[]> exposureByMarket();

    // ---- officer workload / performance ----

    @Query("select c.assignedStaff.id, c.status, count(c) from Claim c where c.assignedStaff is not null "
         + "group by c.assignedStaff.id, c.status")
    List<Object[]> workloadByStaffAndStatus();

    @Query("select c.assignedStaff.id, avg(c.resolutionMinutes) from Claim c "
         + "where c.assignedStaff is not null and c.resolutionMinutes is not null group by c.assignedStaff.id")
    List<Object[]> averageResolutionMinutesByStaff();
}
