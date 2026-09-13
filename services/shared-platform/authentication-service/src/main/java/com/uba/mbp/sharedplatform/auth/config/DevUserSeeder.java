package com.uba.mbp.sharedplatform.auth.config;

import com.uba.mbp.sharedplatform.auth.domain.Role;
import com.uba.mbp.sharedplatform.auth.domain.User;
import com.uba.mbp.sharedplatform.auth.domain.UserRepository;
import com.uba.mbp.sharedplatform.auth.mfa.DevSecrets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.EnumSet;

/**
 * Seeds one dev user per RFP role (ADR-0017) so authentication-service is
 * actually exercisable before Ticket 02's real admin-driven user management
 * exists — the same "committed dev-only credentials, documented as such"
 * pattern ADR-0012 already established for Fineract's mifos/password.
 *
 * <p>Idempotent (checks existence first) so it's safe to run on every
 * startup, and off by default outside local dev via
 * {@code app.seed-dev-users}.
 */
@Component
@ConditionalOnProperty(name = "app.seed-dev-users", havingValue = "true", matchIfMissing = true)
public class DevUserSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevUserSeeder.class);

    /** Dev-only, committed, documented in services/shared-platform/CONTEXT.md — not a real secret. */
    private static final String DEV_PASSWORD = "Dev-Only-Password-123!";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DevUserSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    public void run(String... args) {
        for (Role role : Role.values()) {
            String username = devUsername(role);
            if (userRepository.findByUsername(username).isPresent()) {
                continue;
            }
            String mfaSecret = DevSecrets.forRole(role.name());
            User user = User.enroll(
                    username,
                    passwordEncoder.encode(DEV_PASSWORD),
                    mfaSecret,
                    EnumSet.of(role),
                    clock.instant());
            userRepository.save(user);
            log.info(
                    "Seeded dev user '{}' (role {}) — password and TOTP secret documented in "
                            + "services/shared-platform/CONTEXT.md, dev-only, not a real credential.",
                    username,
                    role);
        }
    }

    private static String devUsername(Role role) {
        return role.name().toLowerCase().replace('_', '-') + ".dev";
    }
}
