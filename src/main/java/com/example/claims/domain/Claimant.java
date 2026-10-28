package com.example.claims.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

@Entity
@Table(name = "claimants", uniqueConstraints =
    @UniqueConstraint(name = "uk_claimants_normalized_email", columnNames = "email"))
public class Claimant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank @Size(max = 120)
    private String fullName;
    @Email @NotBlank
    @Column(nullable = false)
    private String email;
    @NotBlank
    private String phone;

    protected Claimant() {}
    public Claimant(String fullName, String email, String phone) {
        this.fullName = fullName; this.email = normalizeEmail(email); this.phone = phone;
    }
    @PrePersist @PreUpdate
    void normalizeEmail() { email = normalizeEmail(email); }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public void setFullName(String v) { fullName = v; }
    public void setEmail(String v) { email = normalizeEmail(v); }
    public void setPhone(String v) { phone = v; }
}
