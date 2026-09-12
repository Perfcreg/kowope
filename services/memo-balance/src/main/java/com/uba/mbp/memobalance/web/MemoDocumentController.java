package com.uba.mbp.memobalance.web;

import com.uba.mbp.memobalance.exception.MemoAccountNotFoundException;
import com.uba.mbp.memobalance.exception.MemoDocumentNotFoundException;
import com.uba.mbp.memobalance.service.MemoDocumentService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ticket 07: upload and retrieve documents attached to a Memo account (RFP §3.11).
 * Credit Admin and Recovery Team both have "full privileges for verification"
 * (CONTEXT-MAP.md) — non-indebtedness letters and verification records (RFP §3.11)
 * are verification artifacts, so both roles get access; narrower than that isn't
 * asked for by any spec.
 */
@RestController
@RequestMapping("/memo-accounts")
public class MemoDocumentController {

    private static final String DOCUMENT_ROLES = "hasAnyRole('RECOVERY_TEAM', 'CREDIT_ADMIN')";

    private final MemoDocumentService documentService;

    public MemoDocumentController(MemoDocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/{accountNumber}/documents")
    @PreAuthorize(DOCUMENT_ROLES)
    public ResponseEntity<Map<String, UUID>> upload(
            @PathVariable String accountNumber,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        try {
            UUID documentId = documentService.upload(
                    accountNumber, file.getOriginalFilename(), file.getContentType(),
                    file.getBytes(), jwt.getSubject());
            return ResponseEntity.ok(Map.of("documentId", documentId));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @GetMapping("/{accountNumber}/documents")
    @PreAuthorize(DOCUMENT_ROLES)
    public ResponseEntity<List<MemoDocumentSummary>> list(@PathVariable String accountNumber) {
        List<MemoDocumentSummary> documents = documentService.list(accountNumber).stream()
                .map(MemoDocumentSummary::from)
                .toList();
        return ResponseEntity.ok(documents);
    }

    @GetMapping("/{accountNumber}/documents/{documentId}")
    @PreAuthorize(DOCUMENT_ROLES)
    public ResponseEntity<byte[]> download(
            @PathVariable String accountNumber,
            @PathVariable UUID documentId,
            @AuthenticationPrincipal Jwt jwt) {
        var document = documentService.retrieve(accountNumber, documentId, jwt.getSubject());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + document.fileName() + "\"")
                .body(document.content());
    }

    @ExceptionHandler({MemoAccountNotFoundException.class, MemoDocumentNotFoundException.class})
    public ResponseEntity<Void> handleNotFound(RuntimeException e) {
        return ResponseEntity.notFound().build();
    }
}
