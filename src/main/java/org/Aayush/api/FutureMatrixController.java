package org.Aayush.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.Aayush.routing.future.RetainedMatrixResultView;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stage F1 matrix controller for future-aware evaluation and retained-result inspection.
 * Satisfies closure criterion: frontend retrieval flow can inspect retained future-aware matrix results without recomputing them.
 */
@RestController
@Validated
@RequestMapping("/api/v1/matrix")
public class FutureMatrixController {
    private final FutureRoutingApiFacade apiFacade;

    public FutureMatrixController(FutureRoutingApiFacade apiFacade) {
        this.apiFacade = apiFacade;
    }

    /**
     * Stage F1 evaluates one future-aware matrix request through the HTTP surface.
     * Satisfies closure criterion: matrix results can be inspected later without recomputation.
     */
    @PostMapping
    public MatrixApiResponse evaluateMatrix(
            @RequestHeader(FutureRoutingApiFacade.CALLER_HEADER) @NotBlank String callerId,
            @Valid @RequestBody MatrixApiRequest request
    ) {
        return apiFacade.evaluateMatrix(callerId, request);
    }

    /**
     * Stage F1 retrieves retained matrix summary through the HTTP surface.
     * Satisfies closure criterion: frontend retrieval flow can inspect retained matrix results without recomputing them.
     */
    @GetMapping("/results/{resultSetId}/summary")
    public RetainedMatrixResultView.Summary matrixSummary(
            @RequestHeader(FutureRoutingApiFacade.CALLER_HEADER) @NotBlank String callerId,
            @PathVariable("resultSetId") @NotBlank String resultSetId
    ) {
        return apiFacade.matrixSummary(callerId, resultSetId);
    }

    /**
     * Stage F1 retrieves retained matrix detail through the HTTP surface.
     * Satisfies closure criterion: frontend retrieval flow can inspect retained matrix results without recomputing them.
     */
    @GetMapping("/results/{resultSetId}/detail")
    public RetainedMatrixResultView.Detail matrixDetail(
            @RequestHeader(FutureRoutingApiFacade.CALLER_HEADER) @NotBlank String callerId,
            @PathVariable("resultSetId") @NotBlank String resultSetId
    ) {
        return apiFacade.matrixDetail(callerId, resultSetId);
    }
}
