package com.uba.mbp.integration.icad.web;

import com.uba.mbp.integration.icad.clearance.ClearanceRequest;
import com.uba.mbp.integration.icad.clearance.ClearanceRequestAccepted;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Integration spec User Story 6: the capability `clearance-orchestration`
 * calls to request an ICAD name clearance, without knowing ICAD's own
 * push/fetch shape. Restricted to the two "full privilege" RBAC roles
 * (RFP §3.7) until `clearance-orchestration` exists to call this itself.
 */
@RestController
public class IcadClearanceController {

    private final ProducerTemplate producerTemplate;

    public IcadClearanceController(ProducerTemplate producerTemplate) {
        this.producerTemplate = producerTemplate;
    }

    @PostMapping("/icad/clearance-requests")
    @PreAuthorize("hasAnyRole('CREDIT_ADMIN', 'RECOVERY_TEAM')")
    public ResponseEntity<ClearanceRequestAccepted> requestClearance(
            @RequestBody ClearanceRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        ClearanceRequestAccepted outcome = producerTemplate.requestBodyAndHeaders(
                "direct:requestClearance", request, Map.of("actor", jwt.getSubject()), ClearanceRequestAccepted.class);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(outcome);
    }

    @ExceptionHandler(CamelExecutionException.class)
    public ResponseEntity<String> handleIcadFailure(CamelExecutionException e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("ICAD clearance request failed: " + cause.getMessage());
    }
}
