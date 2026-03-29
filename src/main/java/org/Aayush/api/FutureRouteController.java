package org.Aayush.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.Aayush.routing.future.RetainedRouteResultView;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stage F1 route controller for future-aware evaluation and retained-result inspection.
 * Satisfies closure criterion: frontend retrieval flow can inspect retained future-aware route results without recomputing them.
 */
@RestController
@Validated
@RequestMapping("/api/v1/route")
public class FutureRouteController {
    private final FutureRoutingApiFacade apiFacade;

    public FutureRouteController(FutureRoutingApiFacade apiFacade) {
        this.apiFacade = apiFacade;
    }

    /**
     * Stage F1 evaluates one future-aware route request through the HTTP surface.
     * Satisfies closure criterion: route results can be inspected later without recomputation.
     */
    @PostMapping
    public RouteApiResponse evaluateRoute(
            @RequestHeader(FutureRoutingApiFacade.CALLER_HEADER) @NotBlank String callerId,
            @Valid @RequestBody RouteApiRequest request
    ) {
        return apiFacade.evaluateRoute(callerId, request);
    }

    /**
     * Stage F1 retrieves retained route summary through the HTTP surface.
     * Satisfies closure criterion: frontend retrieval flow can inspect retained route results without recomputing them.
     */
    @GetMapping("/results/{resultSetId}/summary")
    public RetainedRouteResultView.Summary routeSummary(
            @RequestHeader(FutureRoutingApiFacade.CALLER_HEADER) @NotBlank String callerId,
            @PathVariable("resultSetId") @NotBlank String resultSetId
    ) {
        return apiFacade.routeSummary(callerId, resultSetId);
    }

    /**
     * Stage F1 retrieves retained route detail through the HTTP surface.
     * Satisfies closure criterion: frontend retrieval flow can inspect retained route results without recomputing them.
     */
    @GetMapping("/results/{resultSetId}/detail")
    public RetainedRouteResultView.Detail routeDetail(
            @RequestHeader(FutureRoutingApiFacade.CALLER_HEADER) @NotBlank String callerId,
            @PathVariable("resultSetId") @NotBlank String resultSetId
    ) {
        return apiFacade.routeDetail(callerId, resultSetId);
    }
}
