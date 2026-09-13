package com.uba.mbp.sharedplatform.auth.security;

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
 * Maps the JWT's {@code roles} claim onto Spring Security authorities — the
 * same claim contract every downstream service's own copy of this converter
 * expects (CONTEXT-MAP.md's shared vocabulary; ADR-0001: no shared library
 * for this, event/HTTP-contract coupling only). Used here so
 * authentication-service can guard its own {@code /admin/**} routes with
 * {@code hasRole("ADMIN")} against the tokens it itself issues.
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
