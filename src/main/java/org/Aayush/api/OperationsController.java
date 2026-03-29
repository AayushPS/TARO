package org.Aayush.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stage F1 operations controller for health and retained-result admin behavior.
 * Satisfies closure criterion: health/admin behavior for future-aware serving is explicit.
 */
@RestController
@RequestMapping("/api/v1")
public final class OperationsController {
    private final FutureRoutingApiFacade apiFacade;

    public OperationsController(FutureRoutingApiFacade apiFacade) {
        this.apiFacade = apiFacade;
    }

    /**
     * Stage F1 returns current API and topology readiness.
     * Satisfies closure criterion: health behavior for future-aware serving is explicit.
     */
    @GetMapping("/health")
    public HealthApiResponse health() {
        return apiFacade.health();
    }

    /**
     * Stage F3 returns low-cardinality operational metrics for serving, retention, and reload posture.
     * Satisfies closure criterion: the system exposes enough metrics and health signal to detect failures in future-aware serving and topology evolution.
     */
    @GetMapping("/metrics")
    public OperationalMetricsResponse metrics() {
        return apiFacade.metrics();
    }

    /**
     * Stage F3 returns explicit rollout and rollback policy for builder-side and serving-side changes.
     * Satisfies closure criterion: rollout posture is explicit for both builder-time and serving-time changes.
     */
    @GetMapping("/governance")
    public OperationalGovernanceResponse governance() {
        return apiFacade.governance();
    }

    /**
     * Stage F1 purges expired retained-result entries through the admin API.
     * Satisfies closure criterion: admin behavior around retained-result access is explicit.
     */
    @PostMapping("/admin/retained-results/purge")
    public AdminPurgeResponse purgeExpiredResults() {
        return apiFacade.purgeExpiredResults();
    }
}
