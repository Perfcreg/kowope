package com.uba.mbp.integration.excelimport.web;

import com.uba.mbp.integration.excelimport.validate.ImportOutcome;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Integration spec User Story 10: the Maxim team's upload endpoint. Only the
 * Maxim Team role has "view and manage Excel data integration processes"
 * privilege (RFP §3.7, CONTEXT-MAP.md's RBAC table).
 */
@RestController
public class ExcelImportController {

    private static final Logger log = LoggerFactory.getLogger(ExcelImportController.class);

    private final ProducerTemplate producerTemplate;

    public ExcelImportController(ProducerTemplate producerTemplate) {
        this.producerTemplate = producerTemplate;
    }

    @PostMapping("/excel-imports")
    @PreAuthorize("hasRole('MAXIM_TEAM')")
    public ResponseEntity<ImportOutcome> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        ImportOutcome outcome = producerTemplate.requestBodyAndHeaders(
                "direct:importExcelFile",
                file.getInputStream(),
                Map.of("actor", jwt.getSubject(), "fileName", sanitizeForLogging(file.getOriginalFilename())),
                ImportOutcome.class);
        return ResponseEntity.ok(outcome);
    }

    /**
     * {@link ProducerTemplate} always wraps whatever the route throws in this
     * — a raw {@code IllegalArgumentException}/{@code UncheckedIOException}
     * handler here would be unreachable dead code, so this is the single path.
     * A malformed upload (bad headers, unparseable rows, wrong file type —
     * POI's own {@code NotOfficeXmlFileException} is itself an
     * {@code IllegalArgumentException}) maps to 400 with its own message;
     * anything else is logged server-side and returned as a generic 500,
     * never echoing internal exception text to the caller.
     */
    @ExceptionHandler(CamelExecutionException.class)
    public ResponseEntity<String> handleCamelFailure(CamelExecutionException e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        if (cause instanceof IllegalArgumentException || cause instanceof UncheckedIOException) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
        }
        log.error("Excel import failed", cause);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Excel import failed — contact support if this persists.");
    }

    /** Strips control characters before a user-supplied filename reaches the audit trail (ADR-0005) or any log line. */
    private String sanitizeForLogging(String value) {
        return value == null ? null : value.replaceAll("\\p{Cntrl}", "_");
    }
}
