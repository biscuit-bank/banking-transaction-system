package com.depi.bank.domain;

import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email", unique = true),
        @Index(name = "idx_users_role", columnList = "role")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 50)
    private String fullName;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 15)
    private Role role;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
    }

    public User(String fullName, String email, String phone, String passwordHash, Role role, Instant createdAt) {
        this.fullName = fullName.trim();
        this.email = email.trim().toLowerCase();
        this.phone = (phone != null && !phone.isBlank()) ? phone.trim() : null;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = createdAt;
    }

    public boolean isAccountLocked(Clock clock) {
        if (lockedUntil == null) {
            return false;
        }
        return Instant.now(clock).isBefore(lockedUntil);
    }

    public boolean recordFailedLoginAttempt(Clock clock, int maxAttempts, int lockMinutes) {
        if (lockedUntil != null && !Instant.now(clock).isBefore(lockedUntil)) {
            failedAttempts = 0;
            lockedUntil = null;
        }
        this.failedAttempts++;
        if (this.failedAttempts >= maxAttempts) {
            this.lockedUntil = Instant.now(clock).plus(lockMinutes, ChronoUnit.MINUTES);
            return true; // Just locked
        }
        return false;
    }

    public void resetFailedAttempts() {
        this.failedAttempts = 0;
        this.lockedUntil = null;
    }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName.trim(); }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = (phone != null && !phone.isBlank()) ? phone.trim() : null; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public int getFailedAttempts() { return failedAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getCreatedAt() { return createdAt; }
}