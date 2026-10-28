package com.example.claims.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "claims_staff")
public class ClaimsStaff {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank private String fullName;
    @Email @Column(nullable = false, unique = true) private String email;
    @Column(nullable = false) private boolean active = true;

    protected ClaimsStaff() {}
    public ClaimsStaff(String fullName, String email) { this.fullName = fullName; this.email = email; }
    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
