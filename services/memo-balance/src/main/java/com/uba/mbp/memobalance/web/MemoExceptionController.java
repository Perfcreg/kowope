package com.uba.mbp.memobalance.web;

import com.uba.mbp.memobalance.exception.MemoAccountNotFoundException;
import com.uba.mbp.memobalance.service.MemoExceptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Spec Story 13 follow-up (post-review): "every detected exception... to
 * appear as an actionable item, so nothing is silently dropped." Exceptions
 * were persisted (Ticket 06) but never exposed — this is that surface, for
 * the roles RFP §3.10 says act on them: Transaction Services and the two
 * full-privilege roles.
 */
@RestController
@RequestMapping("/memo-accounts")
public class MemoExceptionController {

    private final MemoExceptionService exceptionService;

    public MemoExceptionController(MemoExceptionService exceptionService) {
        this.exceptionService = exceptionService;
    }

    @GetMapping("/{accountNumber}/exceptions")
    @PreAuthorize("hasAnyRole('TRANSACTION_SERVICES', 'RECOVERY_TEAM', 'CREDIT_ADMIN')")
    public ResponseEntity<List<MemoExceptionResponse>> list(@PathVariable String accountNumber) {
        List<MemoExceptionResponse> exceptions = exceptionService.list(accountNumber).stream()
                .map(MemoExceptionResponse::from)
                .toList();
        return ResponseEntity.ok(exceptions);
    }

    @ExceptionHandler(MemoAccountNotFoundException.class)
    public ResponseEntity<Void> handleNotFound(MemoAccountNotFoundException e) {
        return ResponseEntity.notFound().build();
    }
}
