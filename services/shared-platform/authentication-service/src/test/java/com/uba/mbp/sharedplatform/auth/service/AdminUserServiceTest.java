package com.uba.mbp.sharedplatform.auth.service;

import com.uba.mbp.audit.AuditEvent;
import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.sharedplatform.auth.domain.Role;
import com.uba.mbp.sharedplatform.auth.domain.User;
import com.uba.mbp.sharedplatform.auth.domain.UserRepository;
import com.uba.mbp.sharedplatform.auth.mfa.TotpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * System-wide-audit fix (2026-09-15): a rejected admin write (duplicate
 * username, last-admin removal) must still leave an audit trail entry — this
 * was the original, unfixed instance of the same gap already fixed once in
 * notification-service and once in reference-data-config.
 */
class AdminUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");

    private UserRepository userRepository;
    private AuditLogger auditLogger;
    private AdminUserService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        auditLogger = mock(AuditLogger.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new AdminUserService(userRepository, mock(org.springframework.security.crypto.password.PasswordEncoder.class),
                mock(TotpService.class), auditLogger, clock);
    }

    @Test
    void rejectsADuplicateUsernameButStillAuditsTheRejection() {
        when(userRepository.findByUsername("csm.dev")).thenReturn(Optional.of(mock(User.class)));

        assertThatThrownBy(() -> service.createUser("csm.dev", "password", Set.of(Role.CSM), "admin-1"))
                .isInstanceOf(UsernameAlreadyExistsException.class);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo("USER_CREATE_REJECTED");
        assertThat(captor.getValue().affectedRecordId()).isEqualTo("csm.dev");
        assertThat(captor.getValue().actor()).isEqualTo("admin-1");
    }

    @Test
    void refusesToRemoveTheLastAdminButStillAuditsTheRejection() {
        User lastAdmin = User.enroll("admin.dev", "hash", "secret", EnumSet.of(Role.ADMIN), NOW);
        when(userRepository.findByUsername("admin.dev")).thenReturn(Optional.of(lastAdmin));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.replaceRoles("admin.dev", Set.of(Role.CSM), "admin-2"))
                .isInstanceOf(LastAdminException.class);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo("USER_ROLES_CHANGE_REJECTED");
        assertThat(captor.getValue().affectedRecordId()).isEqualTo("admin.dev");
        assertThat(captor.getValue().actor()).isEqualTo("admin-2");
    }

    @Test
    void aSuccessfulCreateIsStillAuditedNormally() {
        when(userRepository.findByUsername("new.user")).thenReturn(Optional.empty());
        TotpService totpService = mock(TotpService.class);
        when(totpService.generateSecret()).thenReturn("secret");
        AdminUserService serviceWithRealTotp = new AdminUserService(userRepository,
                mock(org.springframework.security.crypto.password.PasswordEncoder.class), totpService, auditLogger,
                Clock.fixed(NOW, ZoneOffset.UTC));

        serviceWithRealTotp.createUser("new.user", "password", Set.of(Role.CSM), "admin-1");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo("USER_CREATED");
    }
}
