package com.uba.mbp.sharedplatform.notification.web;

import com.icegreen.greenmail.util.GreenMailUtil;
import com.uba.mbp.sharedplatform.notification.AbstractIntegrationTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The real synchronous seam (spec User Story 7): a POST here must produce a
 * real email GreenMail actually receives — not a mocked JavaMailSender.
 */
@AutoConfigureMockMvc
class NotificationControllerFlowTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void resetInbox() throws Exception {
        GREEN_MAIL.reset();
    }

    @Test
    void sendingANotificationDeliversARealEmailToTheResolvedGroup() throws Exception {
        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientGroup":"RECOVERY_TEAM","subject":"Test alert","body":"Something happened."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientGroup").value("RECOVERY_TEAM"))
                .andExpect(jsonPath("$.recipients[0]").value("recovery-team@uba.local"));

        await().atMost(10, SECONDS).untilAsserted(() -> {
            MimeMessage[] received = GREEN_MAIL.getReceivedMessages();
            assertThat(received).hasSize(1);
            assertThat(received[0].getSubject()).isEqualTo("Test alert");
            assertThat(GreenMailUtil.getBody(received[0])).contains("Something happened.");
            assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo("recovery-team@uba.local");
        });
    }

    @Test
    void anUnknownRecipientGroupIsRejectedWithoutSendingAnything() throws Exception {
        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientGroup":"NOT_A_REAL_GROUP","subject":"Test","body":"Body"}
                                """))
                .andExpect(status().isBadRequest());

        assertThat(GREEN_MAIL.getReceivedMessages()).isEmpty();
    }

    @Test
    void aBlankSubjectIsRejectedAsABadRequest() throws Exception {
        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientGroup":"RECOVERY_TEAM","subject":"","body":"Body"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
