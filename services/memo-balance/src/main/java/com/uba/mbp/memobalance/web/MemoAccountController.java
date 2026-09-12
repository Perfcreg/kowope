package com.uba.mbp.memobalance.web;

import com.uba.mbp.memobalance.service.MemoQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ticket 03: RBAC-scoped read access to Memo accounts. CSM has view-only
 * access; Recovery Team and Credit Admin have full privileges (RFP §3.7)
 * which, for this read endpoint, means the same access as CSM.
 */
@RestController
@RequestMapping("/memo-accounts")
public class MemoAccountController {

    private final MemoQueryService queryService;

    public MemoAccountController(MemoQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{accountNumber}")
    @PreAuthorize("hasAnyRole('CSM', 'RECOVERY_TEAM', 'CREDIT_ADMIN')")
    public ResponseEntity<MemoAccountResponse> getByAccountNumber(@PathVariable String accountNumber) {
        return queryService.findByAccountNumber(accountNumber)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
