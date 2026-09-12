package com.uba.mbp.memobalance.web;

import com.uba.mbp.memobalance.exception.InvalidAdjustmentException;
import com.uba.mbp.memobalance.exception.MemoAccountNotFoundException;
import com.uba.mbp.memobalance.service.MemoBalanceAdjustmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ticket 04: records a partial payment or approved write-off against a Memo
 * account. Transaction Services has edit/update privileges over memo balances;
 * Credit Admin has full privileges (RFP §3.7) — both can adjust.
 */
@RestController
@RequestMapping("/memo-accounts")
public class MemoAdjustmentController {

    private final MemoBalanceAdjustmentService adjustmentService;

    public MemoAdjustmentController(MemoBalanceAdjustmentService adjustmentService) {
        this.adjustmentService = adjustmentService;
    }

    @PostMapping("/{accountNumber}/adjustments")
    @PreAuthorize("hasAnyRole('TRANSACTION_SERVICES', 'CREDIT_ADMIN')")
    public ResponseEntity<Void> adjust(
            @PathVariable String accountNumber,
            @RequestBody AdjustmentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        adjustmentService.adjust(accountNumber, request.type(), request.amount(), jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(MemoAccountNotFoundException.class)
    public ResponseEntity<String> handleNotFound(MemoAccountNotFoundException e) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(InvalidAdjustmentException.class)
    public ResponseEntity<String> handleInvalid(InvalidAdjustmentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
