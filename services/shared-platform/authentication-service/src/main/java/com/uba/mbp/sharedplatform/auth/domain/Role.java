package com.uba.mbp.sharedplatform.auth.domain;

/**
 * The RFP §3.7 RBAC role vocabulary (CONTEXT-MAP.md's shared vocabulary table),
 * plus {@link #ADMIN} (ADR-0018): a system-administration persona for the
 * user/role management panel (RFP §4.5's "Admin teams"/"Administrators"),
 * distinct from and carrying no privilege over the 5 business-data roles.
 * Names match exactly what every downstream service's {@code roles} JWT claim
 * already expects — see each service's {@code JwtRoleConverter}. No
 * downstream service checks for {@code ADMIN} specifically, since none of
 * them grant it any business-data access.
 */
public enum Role {
    CSM,
    RECOVERY_TEAM,
    TRANSACTION_SERVICES,
    CREDIT_ADMIN,
    MAXIM_TEAM,
    ADMIN
}
