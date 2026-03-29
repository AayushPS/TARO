package org.Aayush.routing.future;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.Aayush.routing.topology.TopologyVersion;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Stage C5 retained matrix summary/detail contract for later API retrieval work.
 * Satisfies closure criterion: retained results remain topology-aware and safe for API retrieval work.
 */
public final class RetainedMatrixResultView {
    private RetainedMatrixResultView() {
    }

    static Summary summaryOf(FutureMatrixResultSet resultSet) {
        FutureMatrixResultSet nonNullResultSet = Objects.requireNonNull(resultSet, "resultSet");
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
                .aggregate(nonNullResultSet.getAggregate())
                .build();
    }

    static Detail detailOf(FutureMatrixResultSet resultSet) {
        FutureMatrixResultSet nonNullResultSet = Objects.requireNonNull(resultSet, "resultSet");
        ScenarioBundle scenarioBundle = Objects.requireNonNull(nonNullResultSet.getScenarioBundle(), "scenarioBundle");
        return Detail.builder()
                .summary(summaryOf(nonNullResultSet))
                .scenarioBundleGeneratedAt(scenarioBundle.getGeneratedAt())
                .scenarioBundleValidUntil(scenarioBundle.getValidUntil())
                .scenarioBundleHorizonTicks(scenarioBundle.getHorizonTicks())
                .scenarioResults(nonNullResultSet.getScenarioResults())
                .build();
    }

    /**
     * Stage C5 matrix retrieval summary contract for aggregate retained result inspection.
     * Satisfies closure criterion: retained results remain safe for API retrieval work.
     */
    @Value
    @Builder
    public static class Summary {
        String resultSetId;
        Instant createdAt;
        Instant expiresAt;
        FutureMatrixRequest request;
        TopologyVersion topologyVersion;
        String quarantineSnapshotId;
        String scenarioBundleId;
        int scenarioCount;
        FutureMatrixAggregate aggregate;
    }

    /**
     * Stage C5 matrix retrieval detail contract for scenario-level retained matrix inspection.
     * Satisfies closure criterion: retained results remain safe for API retrieval work.
     */
    @Value
    @Builder
    public static class Detail {
        Summary summary;
        Instant scenarioBundleGeneratedAt;
        Instant scenarioBundleValidUntil;
        long scenarioBundleHorizonTicks;
        @Singular("scenarioResult")
        List<FutureMatrixScenarioResult> scenarioResults;
    }
}
