package com.uba.mbp.sharedplatform.auth.service;

import com.uba.mbp.audit.AuditEvent;
import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.sharedplatform.auth.domain.Role;
import com.uba.mbp.sharedplatform.auth.domain.User;
import com.uba.mbp.sharedplatform.auth.domain.UserRepository;
import com.uba.mbp.sharedplatform.auth.mfa.TotpService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * The admin control panel's user-lifecycle operations (RFP §4.5, User
 * Stories 4 and 5) — a distinct responsibility from {@code AuthService}'s
 * login/token flow. Every role/permission change is audited (User Story 5's
 * "log any changes to user roles and permissions"), and the acting admin's
 * identity always comes from their own verified token, never a request field.
 */
@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public AdminUserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TotpService totpService,
            AuditLogger auditLogger,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.totpService = totpService;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    public record CreatedUser(String username, String mfaSecret, Set<Role> roles) {
    }

    /**
     * Creates a real user with a freshly-generated MFA secret, returned exactly once
     * here — an admin hands it to the new user out-of-band for enrollment in their
     * own authenticator app. Never a deterministic dev-only secret (that's
     * {@code DevUserSeeder}'s own, separate concern).
     */
    public CreatedUser createUser(String username, String rawPassword, Set<Role> roles, String actorUsername) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new UsernameAlreadyExistsException("A user named '" + username + "' already exists");
        }
        String mfaSecret = totpService.generateSecret();
        User user = User.enroll(username, passwordEncoder.encode(rawPassword), mfaSecret, roles, clock.instant());
        userRepository.save(user);
        audit(actorUsername, "USER_CREATED", username, "Roles: " + roles);
        return new CreatedUser(username, mfaSecret, roles);
    }

    public record UserSummary(String username, Set<Role> roles, Instant createdAt) {
    }

    public List<UserSummary> listUsers() {
        return userRepository.findAll().stream()
                .map(u -> new UserSummary(u.getUsername(), u.getRoles(), u.getCreatedAt()))
                .toList();
    }

    /**
     * Replaces a user's full role set (RFP §3.7 User Story 5). Refuses a change that
     * would leave the system with zero {@code ADMIN} users — a cheap safeguard
     * against the admin panel locking every administrator out of itself.
     */
    public UserSummary replaceRoles(String targetUsername, Set<Role> newRoles, String actorUsername) {
        User user = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new UserNotFoundException("No user named '" + targetUsername + "'"));

        Set<Role> oldRoles = Set.copyOf(user.getRoles());
        boolean losingAdmin = oldRoles.contains(Role.ADMIN) && !newRoles.contains(Role.ADMIN);
        if (losingAdmin && userRepository.countByRole(Role.ADMIN) <= 1) {
            throw new LastAdminException(
                    "Refusing to remove ADMIN from '" + targetUsername + "': they are the last remaining administrator");
        }

        user.replaceRoles(newRoles, clock.instant());
        userRepository.save(user);
        audit(actorUsername, "USER_ROLES_CHANGED", targetUsername, "Roles " + oldRoles + " -> " + newRoles);
        return new UserSummary(user.getUsername(), user.getRoles(), user.getCreatedAt());
    }

    private void audit(String actor, String action, String affectedUsername, String detail) {
        auditLogger.record(new AuditEvent(
                clock.instant(), actor, action, "User", affectedUsername, "authentication-service", detail));
    }
}
