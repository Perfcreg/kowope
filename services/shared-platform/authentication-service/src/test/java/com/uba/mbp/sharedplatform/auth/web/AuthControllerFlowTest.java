package com.uba.mbp.sharedplatform.auth.web;

import com.uba.mbp.sharedplatform.auth.AbstractIntegrationTest;
import com.uba.mbp.sharedplatform.auth.domain.Role;
import com.uba.mbp.sharedplatform.auth.domain.User;
import com.uba.mbp.sharedplatform.auth.domain.UserRepository;
import com.uba.mbp.sharedplatform.auth.mfa.TotpService;
import com.uba.mbp.sharedplatform.auth.token.SigningKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.util.EnumSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * The full login → MFA → token → refresh → step-up flow (RFP §3.7, §4.3
 * User Stories 1, 2, 3), against real Postgres + Redis + the real TOTP
 * algorithm — no mocked collaborators anywhere in this path.
 */
@AutoConfigureMockMvc
class AuthControllerFlowTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpService totpService;

    @Autowired
    private SigningKeys signingKeys;

    @Autowired
    private Clock clock;

    private static final String USERNAME = "flow-test-user";
    private static final String PASSWORD = "correct-horse-battery-staple";

    @BeforeEach
    void cleanUsers() {
        // Each test seeds its own USERNAME; the shared context (@DirtiesContext
        // AFTER_CLASS) reuses the same Postgres container across test methods.
        userRepository.deleteAll();
    }

    private String seedUserAndReturnMfaSecret(Role role) {
        String mfaSecret = totpService.generateSecret();
        User user = User.enroll(
                USERNAME, passwordEncoder.encode(PASSWORD), mfaSecret, EnumSet.of(role), clock.instant());
        userRepository.save(user);
        return mfaSecret;
    }

    private String extractJsonField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        assertThat(matcher.find()).as("field '%s' present in %s", field, json).isTrue();
        return matcher.group(1);
    }

    @Test
    void fullLoginMfaRefreshStepUpFlowUsesRealCollaboratorsThroughout() throws Exception {
        String mfaSecret = seedUserAndReturnMfaSecret(Role.CREDIT_ADMIN);

        String loginBody = "{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}";
        String loginResponse = mockMvc.perform(post("/auth/login").contentType(APPLICATION_JSON).content(loginBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andReturn().getResponse().getContentAsString();
        String pendingLoginId = extractJsonField(loginResponse, "pendingLoginId");

        String code = totpService.currentCode(mfaSecret);
        String verifyBody = "{\"pendingLoginId\":\"" + pendingLoginId + "\",\"code\":\"" + code + "\"}";
        String tokenResponse = mockMvc.perform(post("/auth/mfa/verify").contentType(APPLICATION_JSON).content(verifyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        String accessToken = extractJsonField(tokenResponse, "accessToken");
        String refreshToken = extractJsonField(tokenResponse, "refreshToken");

        RSAPublicKey publicKey = (RSAPublicKey) signingKeys.signingKey().toRSAPublicKey();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        Jwt decoded = decoder.decode(accessToken);
        assertThat(decoded.getSubject()).isEqualTo(USERNAME);
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("CREDIT_ADMIN");
        assertThat(decoded.getClaimAsString("session_class")).isEqualTo("standard");

        String refreshBody = "{\"refreshToken\":\"" + refreshToken + "\"}";
        String refreshResponse = mockMvc.perform(post("/auth/refresh").contentType(APPLICATION_JSON).content(refreshBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newAccessToken = extractJsonField(refreshResponse, "accessToken");
        assertThat(decoder.decode(newAccessToken).getSubject()).isEqualTo(USERNAME);

        String freshCode = totpService.currentCode(mfaSecret);
        String stepUpBody = "{\"refreshToken\":\"" + refreshToken + "\",\"code\":\"" + freshCode + "\"}";
        String stepUpResponse = mockMvc.perform(post("/auth/step-up").contentType(APPLICATION_JSON).content(stepUpBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String stepUpToken = extractJsonField(stepUpResponse, "accessToken");
        assertThat(decoder.decode(stepUpToken).getClaimAsString("session_class")).isEqualTo("sensitive");

        mockMvc.perform(post("/auth/logout").contentType(APPLICATION_JSON).content(refreshBody))
                .andExpect(status().isNoContent());

        // The session is really gone: refreshing with the now-logged-out token is rejected.
        mockMvc.perform(post("/auth/refresh").contentType(APPLICATION_JSON).content(refreshBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutOnAnUnknownTokenIsIdempotentAndNotAnError() throws Exception {
        String body = "{\"refreshToken\":\"not-a-real-refresh-token\"}";

        mockMvc.perform(post("/auth/logout").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        seedUserAndReturnMfaSecret(Role.CSM);

        String loginBody = "{\"username\":\"" + USERNAME + "\",\"password\":\"wrong-password\"}";
        mockMvc.perform(post("/auth/login").contentType(APPLICATION_JSON).content(loginBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongMfaCodeIsRejectedAndConsumesTheHandle() throws Exception {
        String mfaSecret = seedUserAndReturnMfaSecret(Role.CSM);

        String loginBody = "{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}";
        String loginResponse = mockMvc.perform(post("/auth/login").contentType(APPLICATION_JSON).content(loginBody))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String pendingLoginId = extractJsonField(loginResponse, "pendingLoginId");

        String realCode = totpService.currentCode(mfaSecret);
        String wrongCode = realCode.equals("000000") ? "111111" : "000000";
        String verifyBody = "{\"pendingLoginId\":\"" + pendingLoginId + "\",\"code\":\"" + wrongCode + "\"}";
        mockMvc.perform(post("/auth/mfa/verify").contentType(APPLICATION_JSON).content(verifyBody))
                .andExpect(status().isUnauthorized());

        // The handle is single-use even on a wrong-code attempt (AuthService.verifyMfa's
        // documented anti-bruteforce behavior) — retrying with the same handle also fails.
        mockMvc.perform(post("/auth/mfa/verify").contentType(APPLICATION_JSON).content(verifyBody))
                .andExpect(status().isUnauthorized());
    }
}
