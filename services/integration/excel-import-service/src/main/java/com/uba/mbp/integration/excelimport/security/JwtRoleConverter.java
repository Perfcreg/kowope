package com.uba.mbp.integration.excelimport.security;

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
 * Same claim contract as memo-balance's converter (CONTEXT-MAP.md's shared
 * vocabulary), now issued for real by shared-platform's authentication-service
 * (ADR-0017) — this context's own copy since there's no shared library for
 * Spring Security converters, per ADR-0001's event-driven (not shared-code)
 * coupling between contexts.
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
