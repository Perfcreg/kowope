package com.uba.mbp.sharedplatform.auth.domain;

/**
 * The RFP §3.7 RBAC role vocabulary (CONTEXT-MAP.md's shared vocabulary table).
 * Names match exactly what every downstream service's {@code roles} JWT claim
 * already expects — see each service's {@code JwtRoleConverter}.
 */
public enum Role {
    CSM,
    RECOVERY_TEAM,
    TRANSACTION_SERVICES,
    CREDIT_ADMIN,
    MAXIM_TEAM
}
