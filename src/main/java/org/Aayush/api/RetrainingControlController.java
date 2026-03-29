package org.Aayush.api;

import jakarta.validation.constraints.NotBlank;
import org.Aayush.api.CallerScopedRetainedResultRegistry.ResultKind;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Control-plane HTTP surface for caller-scoped retraining exports, job lifecycle, and active-model publication.
 */
@RestController
@Validated
@RequestMapping("/api/v1/training/retraining")
public class RetrainingControlController {
    private final RetrainingControlService retrainingControlService;

    public RetrainingControlController(RetrainingControlService retrainingControlService) {
        this.retrainingControlService = retrainingControlService;
    }

    @GetMapping("/export")
    public RetrainingTelemetryExportResponse exportTelemetry(
            @RequestHeader(FutureRoutingApiFacade.CALLER_HEADER) @NotBlank String callerId,
            @RequestParam(value = "resultKind", required = false) ResultKind resultKind,
            @RequestParam(value = "topologyVersionId", required = false) String topologyVersionId,
            @RequestParam(value = "scenarioBundleId", required = false) String scenarioBundleId,
            @RequestParam(value = "traitHash", required = false) String traitHash,
            @RequestParam(value = "completeOnly", defaultValue = "false") boolean completeOnly
    ) {
        return retrainingControlService.exportTelemetry(
                callerId,
                resultKind,
                topologyVersionId,
                scenarioBundleId,
                traitHash,
                completeOnly
        );
    }

    @PostMapping("/jobs")
    public RetrainingJobResponse createRetrainingJob(
            @RequestHeader(FutureRoutingApiFacade.CALLER_HEADER) @NotBlank String callerId,
            @RequestBody RetrainingJobCreateRequest request
    ) {
        return retrainingControlService.createRetrainingJob(callerId, request);
    }

    @GetMapping("/jobs/{jobId}")
    public RetrainingJobResponse retrainingJob(
            @RequestHeader(FutureRoutingApiFacade.CALLER_HEADER) @NotBlank String callerId,
            @PathVariable("jobId") @NotBlank String jobId
    ) {
        return retrainingControlService.jobStatus(callerId, jobId);
    }

    @PostMapping("/jobs/{jobId}/start")
    public RetrainingJobResponse startRetrainingJob(
            @RequestHeader(FutureRoutingApiFacade.CALLER_HEADER) @NotBlank String callerId,
            @PathVariable("jobId") @NotBlank String jobId
    ) {
        return retrainingControlService.startRetrainingJob(callerId, jobId);
    }

    @PostMapping("/jobs/{jobId}/complete")
    public RetrainingJobResponse completeRetrainingJob(
            @RequestHeader(FutureRoutingApiFacade.CALLER_HEADER) @NotBlank String callerId,
            @PathVariable("jobId") @NotBlank String jobId,
            @RequestBody RetrainingJobCompletionRequest request
    ) {
        return retrainingControlService.completeRetrainingJob(callerId, jobId, request);
    }

    @PostMapping("/jobs/{jobId}/publish")
    public PublishedServingModelResponse publishRetrainingJob(
            @RequestHeader(FutureRoutingApiFacade.CALLER_HEADER) @NotBlank String callerId,
            @PathVariable("jobId") @NotBlank String jobId
    ) {
        return retrainingControlService.publishRetrainingJob(callerId, jobId);
    }

    @GetMapping("/models/active")
    public PublishedServingModelResponse activeModel(
            @RequestHeader(FutureRoutingApiFacade.CALLER_HEADER) @NotBlank String callerId
    ) {
        return retrainingControlService.activeModel(callerId);
    }
}
