package com.uba.mbp.memobalance.web;

import com.uba.mbp.memobalance.AbstractIntegrationTest;
import com.uba.mbp.memobalance.domain.AdjustmentType;
import com.uba.mbp.memobalance.domain.MemoAccount;
import com.uba.mbp.memobalance.repository.MemoAccountRepository;
import com.uba.mbp.memobalance.service.MemoBalanceAdjustmentService;
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
 * Spec Story 13 follow-up: exceptions raised by Ticket 06 must be visible,
 * not just persisted. Seam under test: the REST API.
 */
@AutoConfigureMockMvc
class MemoExceptionControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemoAccountRepository repository;

    @Autowired
    private MemoBalanceAdjustmentService adjustmentService;

    private RequestPostProcessor withRole(String role) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private void seedAccount(String accountNumber, BigDecimal balance) {
        MemoAccount account = MemoAccount.newlyDetected(
                accountNumber, "CUST-1", "SOL-001", "NGN", "TXN-REF-1",
                "written off", balance, Instant.parse("2026-01-01T00:00:00Z"), Instant.now());
        repository.save(account);
    }

    @Test
    void transactionServicesCanSeeAnUnallocatedPaymentException() throws Exception {
        String accountNumber = "ACC-" + System.nanoTime();
        seedAccount(accountNumber, new BigDecimal("100.00"));

        adjustmentService.adjust(accountNumber, AdjustmentType.PARTIAL_PAYMENT, new BigDecimal("150.00"), "user-1");

        mockMvc.perform(get("/memo-accounts/{accountNumber}/exceptions", accountNumber)
                        .with(withRole("TRANSACTION_SERVICES")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("UNALLOCATED_PAYMENT"))
                .andExpect(jsonPath("$[0].detail").value(org.hamcrest.Matchers.containsString("50.00")));
    }

    @Test
    void aRoleWithoutExceptionVisibilityIsForbidden() throws Exception {
        String accountNumber = "ACC-" + System.nanoTime();
        seedAccount(accountNumber, new BigDecimal("100.00"));

        mockMvc.perform(get("/memo-accounts/{accountNumber}/exceptions", accountNumber)
                        .with(withRole("CSM")))
                .andExpect(status().isForbidden());
    }

    @Test
    void listingExceptionsForAnUnknownAccountIsNotFound() throws Exception {
        mockMvc.perform(get("/memo-accounts/{accountNumber}/exceptions", "ACC-does-not-exist")
                        .with(withRole("CREDIT_ADMIN")))
                .andExpect(status().isNotFound());
    }
}
