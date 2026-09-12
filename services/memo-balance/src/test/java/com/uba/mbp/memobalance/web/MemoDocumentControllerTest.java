package com.uba.mbp.memobalance.web;

import com.uba.mbp.memobalance.AbstractIntegrationTest;
import com.uba.mbp.memobalance.domain.MemoAccount;
import com.uba.mbp.memobalance.repository.MemoAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket 07: upload and retrieve documents for a Memo account. Seam under
 * test: the multipart upload/download REST API (ADR-0009).
 */
@AutoConfigureMockMvc
class MemoDocumentControllerTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void documentStorageDir(DynamicPropertyRegistry registry) {
        registry.add("memo-balance.document-storage.base-dir", () -> {
            try {
                return Files.createTempDirectory("memo-balance-docs-test").toString();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemoAccountRepository repository;

    private RequestPostProcessor withRole(String role) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.subject("user-1").claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private void seedAccount(String accountNumber) {
        MemoAccount account = MemoAccount.newlyDetected(
                accountNumber, "CUST-1", "SOL-001", "NGN", "TXN-REF-1",
                "written off", new BigDecimal("100.00"), Instant.parse("2026-01-01T00:00:00Z"), Instant.now());
        repository.save(account);
    }

    @Test
    void creditAdminCanUploadAndRetrieveADocument() throws Exception {
        String accountNumber = "ACC-" + System.nanoTime();
        seedAccount(accountNumber);

        MockMultipartFile file = new MockMultipartFile(
                "file", "non-indebtedness-letter.txt", "text/plain",
                "confirmed clear".getBytes(StandardCharsets.UTF_8));

        String uploadResponse = mockMvc.perform(multipart("/memo-accounts/{accountNumber}/documents", accountNumber)
                        .file(file)
                        .with(withRole("CREDIT_ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        UUID documentId = extractDocumentId(uploadResponse);

        mockMvc.perform(get("/memo-accounts/{accountNumber}/documents/{documentId}", accountNumber, documentId)
                        .with(withRole("CREDIT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals(
                        "confirmed clear", result.getResponse().getContentAsString()));
    }

    @Test
    void aRoleWithoutDocumentPrivilegeIsForbidden() throws Exception {
        String accountNumber = "ACC-" + System.nanoTime();
        seedAccount(accountNumber);

        MockMultipartFile file = new MockMultipartFile(
                "file", "letter.txt", "text/plain", "content".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/memo-accounts/{accountNumber}/documents", accountNumber)
                        .file(file)
                        .with(withRole("CSM")))
                .andExpect(status().isForbidden());
    }

    @Test
    void downloadingAUnknownDocumentIsNotFound() throws Exception {
        String accountNumber = "ACC-" + System.nanoTime();
        seedAccount(accountNumber);

        mockMvc.perform(get("/memo-accounts/{accountNumber}/documents/{documentId}", accountNumber, UUID.randomUUID())
                        .with(withRole("CREDIT_ADMIN")))
                .andExpect(status().isNotFound());
    }

    private UUID extractDocumentId(String json) {
        Matcher matcher = Pattern.compile("\"documentId\":\"([0-9a-fA-F-]+)\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("No documentId in response: " + json);
        }
        return UUID.fromString(matcher.group(1));
    }
}
