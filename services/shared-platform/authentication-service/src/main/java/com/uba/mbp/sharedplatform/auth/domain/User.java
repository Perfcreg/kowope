package com.uba.mbp.sharedplatform.auth.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * A shared-platform user: the identity, credential, RBAC, and MFA record
 * every other context authenticates against (RFP §3.7, §4.3). The admin
 * control panel (Ticket 02, {@code AdminUserService}) is what mutates
 * {@link #roles} after creation, via {@link #replaceRoles}.
 */
@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /**
     * The user's RFC 6238 TOTP shared secret (Base32), set once at enrollment.
     * MFA is mandatory for every user (RFP §4.3 — no opt-out), so this is
     * never null for a usable account.
     */
    @Column(name = "mfa_secret", nullable = false)
    private String mfaSecret;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Set<Role> roles;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
        // JPA
    }

    public static User enroll(
            String username, String passwordHash, String mfaSecret, Set<Role> roles, Instant now) {
        User user = new User();
        user.username = username;
        user.passwordHash = passwordHash;
        user.mfaSecret = mfaSecret;
        user.roles = EnumSet.copyOf(roles);
        user.createdAt = now;
        user.updatedAt = now;
        return user;
    }

    /** Replaces this user's full role set (the admin panel's role-edit operation, RFP §3.7 User Story 5). */
    public void replaceRoles(Set<Role> newRoles, Instant now) {
        this.roles = EnumSet.copyOf(newRoles);
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getMfaSecret() {
        return mfaSecret;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
