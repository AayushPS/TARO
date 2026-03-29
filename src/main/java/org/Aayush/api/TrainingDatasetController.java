package org.Aayush.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import java.util.List;

/**
 * Caller-scoped CSV dataset intake surface for training configuration.
 */
@RestController
@Validated
@RequestMapping("/api/v1/training/datasets")
public class TrainingDatasetController {
    private final TrainingDatasetService trainingDatasetService;
    private final AdminNotificationService adminNotificationService;

    public TrainingDatasetController(
            TrainingDatasetService trainingDatasetService,
            AdminNotificationService adminNotificationService
    ) {
        this.trainingDatasetService = trainingDatasetService;
        this.adminNotificationService = adminNotificationService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TrainingDatasetResponse uploadDataset(
            @RequestHeader(FutureRoutingApiFacade.CALLER_HEADER) @NotBlank String callerId,
            @RequestParam("file") MultipartFile file
    ) {
        TrainingDatasetResponse response = trainingDatasetService.uploadDataset(callerId, file);
        adminNotificationService.recordDatasetUploaded(callerId, response);
        return response;
    }

    @GetMapping
    public List<TrainingDatasetResponse> datasets(
            @RequestHeader(FutureRoutingApiFacade.CALLER_HEADER) @NotBlank String callerId,
            @RequestParam(value = "limit", defaultValue = "20") @Positive int limit
    ) {
        List<TrainingDatasetResponse> datasets = trainingDatasetService.datasets(callerId);
        return datasets.size() <= limit ? datasets : datasets.subList(0, limit);
    }
}
