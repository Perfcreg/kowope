package com.uba.mbp.sharedplatform.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Maps a recipient group name (e.g. {@code RECOVERY_TEAM}, {@code CREDIT_ADMIN},
 * {@code OPERATIONS}) to the email addresses that receive it. A static,
 * configured list — there is no real staff-directory/HR system anywhere in
 * this repo (not even {@code reference-data-config}) to resolve this against
 * for real, so this is the honest equivalent of authentication-service's
 * {@code DevUserSeeder}: a real, working mechanism for local/dev, not a
 * production-grade directory lookup. See ADR-0019.
 */
@ConfigurationProperties(prefix = "notification.recipients")
public class NotificationRecipientsProperties {

    private Map<String, List<String>> groups = Map.of();

    public Map<String, List<String>> getGroups() {
        return groups;
    }

    public void setGroups(Map<String, List<String>> groups) {
        this.groups = groups;
    }
}
