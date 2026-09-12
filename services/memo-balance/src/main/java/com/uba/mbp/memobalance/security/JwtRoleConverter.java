package com.uba.mbp.memobalance.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps the JWT's {@code roles} claim onto Spring Security authorities.
 * The claim contract (a "roles" array of RBAC role names — CSM, RECOVERY_TEAM,
 * TRANSACTION_SERVICES, CREDIT_ADMIN, MAXIM_TEAM, per CONTEXT-MAP.md's shared
 * vocabulary) is defined here because shared-platform's authentication-service
 * doesn't exist yet; this converter is the seam that will keep working once it does.
 */
public class JwtRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        Collection<GrantedAuthority> authorities = (roles == null ? List.<String>of() : roles).stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
        return new JwtAuthenticationToken(jwt, authorities);
    }
}
