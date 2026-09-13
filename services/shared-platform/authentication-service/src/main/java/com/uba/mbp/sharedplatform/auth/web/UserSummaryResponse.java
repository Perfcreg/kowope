package com.uba.mbp.sharedplatform.auth.web;

import com.uba.mbp.sharedplatform.auth.service.AdminUserService;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

/** Never carries a password hash or MFA secret — those never leave {@code createUser}'s one-time response. */
public record UserSummaryResponse(String username, Set<String> roles, Instant createdAt) {
    public static UserSummaryResponse from(AdminUserService.UserSummary summary) {
        Set<String> roleNames = summary.roles().stream().map(Enum::name).collect(Collectors.toSet());
        return new UserSummaryResponse(summary.username(), roleNames, summary.createdAt());
    }
}
