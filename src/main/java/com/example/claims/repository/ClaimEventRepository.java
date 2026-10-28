package com.example.claims.repository;

import com.example.claims.domain.ClaimEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClaimEventRepository extends JpaRepository<ClaimEvent, Long> {
    List<ClaimEvent> findByClaimIdOrderByCreatedAtAscIdAsc(Long claimId);
}
