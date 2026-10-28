package com.example.claims.repository;
import com.example.claims.domain.Claimant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface ClaimantRepository extends JpaRepository<Claimant, Long> {
    /** Match canonical email while tolerating case/whitespace in legacy rows. */
    @Query("select c from Claimant c where lower(trim(c.email)) = :email")
    Optional<Claimant> findByEmailIgnoreCase(@Param("email") String email);
}
