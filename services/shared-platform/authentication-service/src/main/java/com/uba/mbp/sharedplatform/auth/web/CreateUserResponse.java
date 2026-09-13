package com.uba.mbp.sharedplatform.auth.web;

import com.uba.mbp.sharedplatform.auth.service.AdminUserService;

import java.util.Set;
import java.util.stream.Collectors;

/** {@code mfaSecret} is exposed exactly once, here — the admin hands it to the new user out-of-band for enrollment. */
public record CreateUserResponse(String username, String mfaSecret, Set<String> roles) {
    public static CreateUserResponse from(AdminUserService.CreatedUser created) {
        Set<String> roleNames = created.roles().stream().map(Enum::name).collect(Collectors.toSet());
        return new CreateUserResponse(created.username(), created.mfaSecret(), roleNames);
    }
}
