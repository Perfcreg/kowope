package com.uba.mbp.integration.excelimport.web;

import com.uba.mbp.integration.excelimport.validate.ImportOutcome;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
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
                Map.of("actor", jwt.getSubject(), "fileName", file.getOriginalFilename()),
                ImportOutcome.class);
        return ResponseEntity.ok(outcome);
    }

    @ExceptionHandler({IllegalArgumentException.class, UncheckedIOException.class})
    public ResponseEntity<String> handleMalformedUpload(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    /** ProducerTemplate wraps whatever the route throws — unwrap so the two handlers above still apply. */
    @ExceptionHandler(CamelExecutionException.class)
    public ResponseEntity<String> handleCamelFailure(CamelExecutionException e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        if (cause instanceof IllegalArgumentException || cause instanceof UncheckedIOException) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Excel import failed: " + cause.getMessage());
    }
}
