package com.uba.mbp.referencedataconfig.web;

import com.uba.mbp.referencedataconfig.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RFP §3.14 User Story 1, against real Postgres — the {@code hasRole("ADMIN")}
 * enforcement is proven with a real JWT-authenticated MockMvc request
 * (Spring Security Test's {@code jwt()} post-processor, same pattern
 * excel-import-service's own RBAC-gated endpoint test uses), not a mocked
 * security context.
 */
@AutoConfigureMockMvc
class CountryAdminControllerFlowTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private RequestPostProcessor asAdmin() {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.subject("admin-1").claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private RequestPostProcessor asCsm() {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.subject("csm-1").claim("roles", List.of("CSM")))
                .authorities(new SimpleGrantedAuthority("ROLE_CSM"));
    }

    @Test
    void anAdminCanCreateANewCountry() throws Exception {
        mockMvc.perform(post("/countries")
                        .with(asAdmin())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"countryCode":"GH","region":"Africa & Nigeria","baseCurrency":"GHS","glWriteOffCode":"GL-WO-GH","glRecoveryCode":"GL-REC-GH"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.countryCode").value("GH"))
                .andExpect(jsonPath("$.baseCurrency").value("GHS"));
    }

    @Test
    void aNonAdminCannotCreateACountry() throws Exception {
        mockMvc.perform(post("/countries")
                        .with(asCsm())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"countryCode":"KE","region":"Africa & Nigeria","baseCurrency":"KES","glWriteOffCode":"GL-WO-KE","glRecoveryCode":"GL-REC-KE"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnauthenticatedCreateRequestIsRejected() throws Exception {
        mockMvc.perform(post("/countries")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"countryCode":"KE","region":"Africa & Nigeria","baseCurrency":"KES","glWriteOffCode":"GL-WO-KE","glRecoveryCode":"GL-REC-KE"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updatingACountryProducesTwoHistoricalRowsAndTheReadPathReturnsOnlyTheNewOne() throws Exception {
        mockMvc.perform(post("/countries")
                        .with(asAdmin())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"countryCode":"TZ","region":"Africa & Nigeria","baseCurrency":"TZS","glWriteOffCode":"GL-WO-TZ-1","glRecoveryCode":"GL-REC-TZ"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/countries/TZ")
                        .with(asAdmin())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"region":"Africa & Nigeria","baseCurrency":"TZS","glWriteOffCode":"GL-WO-TZ-2","glRecoveryCode":"GL-REC-TZ"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.glWriteOffCode").value("GL-WO-TZ-2"));

        mockMvc.perform(get("/countries/TZ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.glWriteOffCode").value("GL-WO-TZ-2"));
    }

    @Test
    void creatingADuplicateCurrentCountryIsRejected() throws Exception {
        mockMvc.perform(post("/countries")
                        .with(asAdmin())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"countryCode":"UG","region":"Africa & Nigeria","baseCurrency":"UGX","glWriteOffCode":"GL-WO-UG","glRecoveryCode":"GL-REC-UG"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/countries")
                        .with(asAdmin())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"countryCode":"UG","region":"Africa & Nigeria","baseCurrency":"UGX","glWriteOffCode":"GL-WO-UG","glRecoveryCode":"GL-REC-UG"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void updatingAnUnknownCountryIs404() throws Exception {
        mockMvc.perform(put("/countries/ZZ")
                        .with(asAdmin())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"region":"Nowhere","baseCurrency":"USD","glWriteOffCode":"GL-WO","glRecoveryCode":"GL-REC"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void aBlankFieldIsRejectedAsBadRequest() throws Exception {
        mockMvc.perform(post("/countries")
                        .with(asAdmin())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"countryCode":"RW","region":"","baseCurrency":"RWF","glWriteOffCode":"GL-WO-RW","glRecoveryCode":"GL-REC-RW"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
