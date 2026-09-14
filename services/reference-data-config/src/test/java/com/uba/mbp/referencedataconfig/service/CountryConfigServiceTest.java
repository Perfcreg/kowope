package com.uba.mbp.referencedataconfig.service;

import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.referencedataconfig.domain.Country;
import com.uba.mbp.referencedataconfig.domain.CountryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CountryConfigServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");

    private CountryRepository repository;
    private AuditLogger auditLogger;
    private CountryConfigService service;

    @BeforeEach
    void setUp() {
        repository = mock(CountryRepository.class);
        auditLogger = mock(AuditLogger.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new CountryConfigService(repository, auditLogger, clock);
        when(repository.save(any(Country.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.saveAndFlush(any(Country.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsANewCountryAndAudits() {
        when(repository.existsByCountryCodeAndEffectiveToIsNull("NG")).thenReturn(false);

        Country created = service.create("NG", "Africa & Nigeria", "NGN", "GL-WO-NG", "GL-REC-NG", "admin-1");

        assertThat(created.getCountryCode()).isEqualTo("NG");
        assertThat(created.getEffectiveFrom()).isEqualTo(NOW);
        assertThat(created.getEffectiveTo()).isNull();
        verify(auditLogger, times(1)).record(any());
    }

    @Test
    void rejectsCreatingACountryThatAlreadyHasACurrentMapping() {
        when(repository.existsByCountryCodeAndEffectiveToIsNull("NG")).thenReturn(true);

        assertThatThrownBy(() -> service.create("NG", "Africa & Nigeria", "NGN", "GL-WO-NG", "GL-REC-NG", "admin-1"))
                .isInstanceOf(CountryAlreadyExistsException.class);

        verify(auditLogger, never()).record(any());
    }

    @Test
    void updateClosesThePriorRowAndOpensANewOneInsteadOfMutatingInPlace() {
        Country existing = Country.open("NG", "Africa & Nigeria", "NGN", "GL-WO-NG", "GL-REC-NG",
                NOW.minusSeconds(3600), "admin-1");
        when(repository.findByCountryCodeAndEffectiveToIsNull("NG")).thenReturn(Optional.of(existing));

        Country updated = service.update("NG", "Africa & Nigeria", "NGN", "GL-WO-NG-2", "GL-REC-NG", "admin-2");

        assertThat(existing.getEffectiveTo()).isEqualTo(NOW);
        assertThat(updated.getGlWriteOffCode()).isEqualTo("GL-WO-NG-2");
        assertThat(updated.getEffectiveFrom()).isEqualTo(NOW);
        assertThat(updated.getEffectiveTo()).isNull();

        verify(repository, times(1)).saveAndFlush(existing);
        verify(repository, times(1)).save(any(Country.class));
    }

    @Test
    void updatingAnUnknownCountryThrowsNotFound() {
        when(repository.findByCountryCodeAndEffectiveToIsNull("ZZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("ZZ", "Region", "NGN", "GL-WO", "GL-REC", "admin-1"))
                .isInstanceOf(CountryNotFoundException.class);
    }

    @Test
    void aBlankFieldIsRejectedBeforeTouchingTheRepository() {
        assertThatThrownBy(() -> service.create("NG", "Africa & Nigeria", " ", "GL-WO-NG", "GL-REC-NG", "admin-1"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void findCurrentReturnsTheOpenRow() {
        Country existing = Country.open("NG", "Africa & Nigeria", "NGN", "GL-WO-NG", "GL-REC-NG", NOW, "admin-1");
        when(repository.findByCountryCodeAndEffectiveToIsNull("NG")).thenReturn(Optional.of(existing));

        assertThat(service.findCurrent("NG")).isEqualTo(existing);
    }

    @Test
    void findCurrentThrowsForAnUnconfiguredCountry() {
        when(repository.findByCountryCodeAndEffectiveToIsNull("ZZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findCurrent("ZZ")).isInstanceOf(CountryNotFoundException.class);
    }
}
