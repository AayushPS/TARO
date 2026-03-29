package org.Aayush.routing.future;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.Aayush.routing.topology.TopologyVersion;

import java.time.Instant;
import java.util.Objects;
import java.util.List;

/**
 * Stage C5 retained route summary/detail contract for later API retrieval work.
 * Satisfies closure criterion: retained results remain topology-aware and safe for API retrieval work.
 */
public final class RetainedRouteResultView {
    private RetainedRouteResultView() {
    }

    static Summary summaryOf(FutureRouteResultSet resultSet) {
        FutureRouteResultSet nonNullResultSet = Objects.requireNonNull(resultSet, "resultSet");
        ScenarioBundle scenarioBundle = Objects.requireNonNull(nonNullResultSet.getScenarioBundle(), "scenarioBundle");
        return Summary.builder()
                .resultSetId(nonNullResultSet.getResultSetId())
                .createdAt(nonNullResultSet.getCreatedAt())
                .expiresAt(nonNullResultSet.getExpiresAt())
                .request(nonNullResultSet.getRequest())
                .topologyVersion(nonNullResultSet.getTopologyVersion())
                .quarantineSnapshotId(nonNullResultSet.getQuarantineSnapshotId())
                .scenarioBundleId(scenarioBundle.getScenarioBundleId())
                .scenarioCount(scenarioBundle.getScenarios().size())
                .expectedRoute(nonNullResultSet.getExpectedRoute())
                .robustRoute(nonNullResultSet.getRobustRoute())
                .alternatives(nonNullResultSet.getAlternatives())
                .build();
    }

    static Detail detailOf(FutureRouteResultSet resultSet) {
        FutureRouteResultSet nonNullResultSet = Objects.requireNonNull(resultSet, "resultSet");
        ScenarioBundle scenarioBundle = Objects.requireNonNull(nonNullResultSet.getScenarioBundle(), "scenarioBundle");
        return Detail.builder()
                .summary(summaryOf(nonNullResultSet))
                .scenarioBundleGeneratedAt(scenarioBundle.getGeneratedAt())
                .scenarioBundleValidUntil(scenarioBundle.getValidUntil())
                .scenarioBundleHorizonTicks(scenarioBundle.getHorizonTicks())
                .candidateDensityCalibrationReport(nonNullResultSet.getCandidateDensityCalibrationReport())
                .scenarioResults(nonNullResultSet.getScenarioResults())
                .build();
    }

    /**
     * Stage C5 route retrieval summary contract for frontend-friendly retained result inspection.
     * Satisfies closure criterion: retained results remain safe for API retrieval work.
     */
    @Value
    @Builder
    public static class Summary {
        String resultSetId;
        Instant createdAt;
        Instant expiresAt;
        FutureRouteRequest request;
        TopologyVersion topologyVersion;
        String quarantineSnapshotId;
        String scenarioBundleId;
        int scenarioCount;
        ScenarioRouteSelection expectedRoute;
        ScenarioRouteSelection robustRoute;
        @Singular("alternative")
        List<ScenarioRouteSelection> alternatives;
    }

    /**
     * Stage C5 route retrieval detail contract for scenario-level retained result inspection.
     * Satisfies closure criterion: retained results remain safe for API retrieval work.
     */
    @Value
    @Builder
    public static class Detail {
        Summary summary;
        Instant scenarioBundleGeneratedAt;
        Instant scenarioBundleValidUntil;
        long scenarioBundleHorizonTicks;
        CandidateDensityCalibrationReport candidateDensityCalibrationReport;
        @Singular("scenarioResult")
        List<FutureRouteScenarioResult> scenarioResults;
    }
}
