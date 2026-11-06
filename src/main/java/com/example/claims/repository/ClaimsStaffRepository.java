package com.example.claims.repository;
import com.example.claims.domain.ClaimsStaff;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ClaimsStaffRepository extends JpaRepository<ClaimsStaff, Long> {
    boolean existsByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ClaimsStaff s where s.id = :id")
    Optional<ClaimsStaff> findByIdForUpdate(@Param("id") Long id);
}
