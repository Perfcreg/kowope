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
import java.util.Map;
import java.util.UUID;

/**
 * Ticket 07: upload and retrieve documents attached to a Memo account (RFP §3.11).
 * Restricted to Credit Admin, matching the ticket's own scope — broaden to other
 * roles only when a spec actually asks for it.
 */
@RestController
@RequestMapping("/memo-accounts")
public class MemoDocumentController {

    private final MemoDocumentService documentService;

    public MemoDocumentController(MemoDocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/{accountNumber}/documents")
    @PreAuthorize("hasRole('CREDIT_ADMIN')")
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

    @GetMapping("/{accountNumber}/documents/{documentId}")
    @PreAuthorize("hasRole('CREDIT_ADMIN')")
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
