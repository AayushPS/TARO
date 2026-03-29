package org.Aayush.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Caller-scoped notification feed for admin-side training lifecycle updates.
 */
@RestController
@Validated
@RequestMapping("/api/v1/training/notifications")
public class AdminNotificationController {
    private final AdminNotificationService adminNotificationService;

    public AdminNotificationController(AdminNotificationService adminNotificationService) {
        this.adminNotificationService = adminNotificationService;
    }

    @GetMapping
    public List<AdminNotificationResponse> notifications(
            @RequestHeader(FutureRoutingApiFacade.CALLER_HEADER) @NotBlank String callerId,
            @RequestParam(value = "limit", defaultValue = "20") @Positive int limit
    ) {
        return adminNotificationService.notifications(callerId, limit);
    }
}
