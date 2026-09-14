package com.uba.mbp.sharedplatform.notification.web;

import com.uba.mbp.sharedplatform.notification.service.NotificationResult;
import com.uba.mbp.sharedplatform.notification.service.NotificationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The single place every context sends an alert or notification through
 * (RFP §3.4). Deliberately not RBAC-gated: unlike every other REST endpoint
 * in this repo, this one is called service-to-service (by the four
 * `integration` adapters today, other backend contexts later), not by a
 * human or the `channels` SPA — see ADR-0019 for why that's a real decision
 * and not an oversight.
 */
@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/notifications")
    public NotificationResult send(@RequestBody NotificationRequest request) {
        return notificationService.send(request.recipientGroup(), request.subject(), request.body());
    }
}
