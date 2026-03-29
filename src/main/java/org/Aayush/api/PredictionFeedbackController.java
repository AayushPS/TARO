package org.Aayush.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stage F2 feedback controller for joining eventual route and matrix outcomes back to served predictions.
 * Satisfies closure criterion: served predictions can be joined back to outcome telemetry through the canonical HTTP surface.
 */
@RestController
@Validated
@RequestMapping("/api/v1/feedback")
public class PredictionFeedbackController {
    private final FutureRoutingApiFacade apiFacade;

    public PredictionFeedbackController(FutureRoutingApiFacade apiFacade) {
        this.apiFacade = apiFacade;
    }

    /**
     * Stage F2 records one route outcome update through the HTTP/API surface.
     * Satisfies closure criterion: eventual route outcomes join back to stable served-prediction lineage.
     */
    @PostMapping("/route/results/{resultSetId}/outcome")
    public PredictionFeedbackResponse routeOutcome(
            @RequestHeader(FutureRoutingApiFacade.CALLER_HEADER) @NotBlank String callerId,
            @PathVariable("resultSetId") @NotBlank String resultSetId,
            @Valid @RequestBody PredictionFeedbackRequest request
    ) {
        return apiFacade.recordRouteFeedback(callerId, resultSetId, request);
    }

    /**
     * Stage F2 records one matrix outcome update through the HTTP/API surface.
     * Satisfies closure criterion: eventual matrix outcomes join back to stable served-prediction lineage.
     */
    @PostMapping("/matrix/results/{resultSetId}/outcome")
    public PredictionFeedbackResponse matrixOutcome(
            @RequestHeader(FutureRoutingApiFacade.CALLER_HEADER) @NotBlank String callerId,
            @PathVariable("resultSetId") @NotBlank String resultSetId,
            @Valid @RequestBody PredictionFeedbackRequest request
    ) {
        return apiFacade.recordMatrixFeedback(callerId, resultSetId, request);
    }
}
