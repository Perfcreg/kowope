package com.uba.mbp.sharedplatform.auth.web;

import com.uba.mbp.sharedplatform.auth.domain.Role;
import com.uba.mbp.sharedplatform.auth.service.AdminUserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The admin control panel (RFP §4.5, ADR-0018): user creation, listing, and
 * role management, gated on {@code ROLE_ADMIN} (see SecurityConfig). The
 * acting admin's identity always comes from their own verified token
 * ({@link Jwt#getSubject()}), never a request field — the audit trail
 * (User Story 5) is only as trustworthy as that actor identity.
 */
@RestController
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @PostMapping("/admin/users")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateUserResponse createUser(@RequestBody CreateUserRequest request, @AuthenticationPrincipal Jwt admin) {
        Set<Role> roles = parseRoles(request.roles());
        AdminUserService.CreatedUser created =
                adminUserService.createUser(request.username(), request.password(), roles, admin.getSubject());
        Set<String> roleNames = created.roles().stream().map(Enum::name).collect(Collectors.toSet());
        return new CreateUserResponse(created.username(), created.mfaSecret(), roleNames);
    }

    @GetMapping("/admin/users")
    public List<UserSummaryResponse> listUsers() {
        return adminUserService.listUsers().stream().map(UserSummaryResponse::from).toList();
    }

    @PutMapping("/admin/users/{username}/roles")
    public UserSummaryResponse updateRoles(
            @PathVariable String username, @RequestBody UpdateRolesRequest request, @AuthenticationPrincipal Jwt admin) {
        Set<Role> roles = parseRoles(request.roles());
        AdminUserService.UserSummary updated = adminUserService.replaceRoles(username, roles, admin.getSubject());
        return UserSummaryResponse.from(updated);
    }

    private static Set<Role> parseRoles(Set<String> roleNames) {
        return roleNames.stream().map(Role::valueOf).collect(Collectors.toSet());
    }
}
