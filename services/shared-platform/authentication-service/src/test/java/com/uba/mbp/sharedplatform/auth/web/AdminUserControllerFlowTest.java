package com.uba.mbp.sharedplatform.auth.web;

import com.uba.mbp.sharedplatform.auth.AbstractIntegrationTest;
import com.uba.mbp.sharedplatform.auth.domain.Role;
import com.uba.mbp.sharedplatform.auth.domain.User;
import com.uba.mbp.sharedplatform.auth.domain.UserRepository;
import com.uba.mbp.sharedplatform.auth.token.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.util.EnumSet;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin control panel (RFP §4.5, ADR-0018), against real Postgres — a
 * real signed token (minted via the already-tested {@link TokenService},
 * not a mocked security context) proves the {@code hasRole("ADMIN")}
 * enforcement in {@code SecurityConfig} actually works end to end.
 */
@AutoConfigureMockMvc
class AdminUserControllerFlowTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private Clock clock;

    @BeforeEach
    void cleanUsers() {
        userRepository.deleteAll();
    }

    private String bearerFor(String username, Role role) {
        return "Bearer " + tokenService.issueAccessToken(username, EnumSet.of(role));
    }

    private void seedUser(String username, Role role) {
        userRepository.save(User.enroll(
                username, passwordEncoder.encode("irrelevant"), "IRRELEVANTSECRET", EnumSet.of(role), clock.instant()));
    }

    @Test
    void adminCanCreateListAndChangeRoles() throws Exception {
        String adminToken = bearerFor("admin-test", Role.ADMIN);
        seedUser("admin-test", Role.ADMIN); // countByRole safeguard needs a real persisted admin

        String createBody = "{\"username\":\"new-csm\",\"password\":\"Some-Password-1!\",\"roles\":[\"CSM\"]}";
        mockMvc.perform(post("/admin/users").header(AUTHORIZATION, adminToken)
                        .contentType(APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("new-csm"))
                .andExpect(jsonPath("$.mfaSecret").exists())
                .andExpect(jsonPath("$.roles[0]").value("CSM"));

        mockMvc.perform(get("/admin/users").header(AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == 'new-csm')]").exists());

        String updateBody = "{\"roles\":[\"CREDIT_ADMIN\"]}";
        mockMvc.perform(put("/admin/users/new-csm/roles").header(AUTHORIZATION, adminToken)
                        .contentType(APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("CREDIT_ADMIN"));
    }

    @Test
    void nonAdminRoleIsForbidden() throws Exception {
        String csmToken = bearerFor("csm-test", Role.CSM);

        mockMvc.perform(get("/admin/users").header(AUTHORIZATION, csmToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void noTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void creatingADuplicateUsernameIsConflict() throws Exception {
        String adminToken = bearerFor("admin-test", Role.ADMIN);
        seedUser("admin-test", Role.ADMIN);
        seedUser("already-exists", Role.CSM);

        String createBody = "{\"username\":\"already-exists\",\"password\":\"Some-Password-1!\",\"roles\":[\"CSM\"]}";
        mockMvc.perform(post("/admin/users").header(AUTHORIZATION, adminToken)
                        .contentType(APPLICATION_JSON).content(createBody))
                .andExpect(status().isConflict());
    }

    @Test
    void removingTheLastAdminIsRejected() throws Exception {
        String adminToken = bearerFor("only-admin", Role.ADMIN);
        seedUser("only-admin", Role.ADMIN);

        String updateBody = "{\"roles\":[\"CSM\"]}";
        mockMvc.perform(put("/admin/users/only-admin/roles").header(AUTHORIZATION, adminToken)
                        .contentType(APPLICATION_JSON).content(updateBody))
                .andExpect(status().isConflict());
    }

    @Test
    void changingRolesForAnUnknownUserIs404() throws Exception {
        String adminToken = bearerFor("admin-test", Role.ADMIN);
        seedUser("admin-test", Role.ADMIN);

        String updateBody = "{\"roles\":[\"CSM\"]}";
        mockMvc.perform(put("/admin/users/nobody-here/roles").header(AUTHORIZATION, adminToken)
                        .contentType(APPLICATION_JSON).content(updateBody))
                .andExpect(status().isNotFound());
    }
}
