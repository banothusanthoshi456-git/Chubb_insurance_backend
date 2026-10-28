package com.example.claims.repository;
import com.example.claims.domain.ClaimsStaff;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimsStaffRepository extends JpaRepository<ClaimsStaff, Long> {
    boolean existsByEmailIgnoreCase(String email);
}
