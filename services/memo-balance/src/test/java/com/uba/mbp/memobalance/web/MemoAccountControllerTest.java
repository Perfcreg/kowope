package com.uba.mbp.memobalance.web;

import com.uba.mbp.memobalance.AbstractIntegrationTest;
import com.uba.mbp.memobalance.domain.MemoAccount;
import com.uba.mbp.memobalance.repository.MemoAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket 03: RBAC-scoped REST API to query Memo accounts, balance, and history.
 * Seam under test: the REST API, with a real authenticated (mocked-JWT) request.
 * Country-scoping (RFP §3.14) is deferred until reference-data-config exists —
 * only role-based access is verified here (see SecurityConfig's class comment).
 */
@AutoConfigureMockMvc
class MemoAccountControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemoAccountRepository repository;

    /**
     * SecurityMockMvcRequestPostProcessors.jwt() injects the Authentication directly,
     * bypassing our real JwtRoleConverter — so the test must supply the same
     * ROLE_-prefixed authority the converter would have produced, or every
     * @PreAuthorize check sees an unauthenticated-for-that-role principal.
     */
    private RequestPostProcessor withRole(String role) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private MemoAccount seedAccount(String accountNumber) {
        MemoAccount account = MemoAccount.newlyDetected(
                accountNumber, "CUST-1", "SOL-001", "NGN", "TXN-REF-1",
                "written off", new BigDecimal("5000.00"),
                Instant.parse("2026-01-01T00:00:00Z"), Instant.now());
        account.applyCountryConfig("NG", "NGN", "GL-WRITEOFF-NG", "GL-RECOVERY-NG");
        return repository.save(account);
    }

    @Test
    void csmCanReadAMemoAccountViewOnly() throws Exception {
        String accountNumber = "ACC-" + System.nanoTime();
        seedAccount(accountNumber);

        mockMvc.perform(get("/memo-accounts/{accountNumber}", accountNumber)
                        .with(withRole("CSM")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value(accountNumber))
                .andExpect(jsonPath("$.status").value("IMPORTED_PENDING_REVIEW"))
                .andExpect(jsonPath("$.balance").value(5000.00))
                .andExpect(jsonPath("$.country").value("NG"))
                .andExpect(jsonPath("$.glWriteOffCode").value("GL-WRITEOFF-NG"))
                .andExpect(jsonPath("$.history").isArray());
    }

    @Test
    void transactionServicesCanReadAMemoAccountItEdits() throws Exception {
        // RBAC reconciliation: Transaction Services has "edit/update privileges
        // for account data and memo balances" (CONTEXT-MAP.md) but previously had
        // no way to read what it was editing.
        String accountNumber = "ACC-" + System.nanoTime();
        seedAccount(accountNumber);

        mockMvc.perform(get("/memo-accounts/{accountNumber}", accountNumber)
                        .with(withRole("TRANSACTION_SERVICES")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value(accountNumber));
    }

    @Test
    void creditAdminCanReadAMemoAccount() throws Exception {
        String accountNumber = "ACC-" + System.nanoTime();
        seedAccount(accountNumber);

        mockMvc.perform(get("/memo-accounts/{accountNumber}", accountNumber)
                        .with(withRole("CREDIT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value(accountNumber));
    }

    @Test
    void aRoleWithoutReadPrivilegeIsForbidden() throws Exception {
        String accountNumber = "ACC-" + System.nanoTime();
        seedAccount(accountNumber);

        mockMvc.perform(get("/memo-accounts/{accountNumber}", accountNumber)
                        .with(withRole("MAXIM_TEAM")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/memo-accounts/{accountNumber}", "ACC-does-not-matter"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aNonExistentAccountIsNotFound() throws Exception {
        mockMvc.perform(get("/memo-accounts/{accountNumber}", "ACC-does-not-exist")
                        .with(withRole("CSM")))
                .andExpect(status().isNotFound());
    }
}
