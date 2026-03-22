package org.Aayush.routing.future;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Shared validation for retained future-aware route result contracts.
 */
final class FutureRouteResultSupport {
    private static final double PROBABILITY_TOLERANCE = 1.0e-6d;

    private FutureRouteResultSupport() {
    }

    static void validateRouteResultSet(FutureRouteResultSet resultSet) {
        FutureRouteResultSet nonNullResultSet = Objects.requireNonNull(resultSet, "resultSet");
        requireNonBlank(nonNullResultSet.getResultSetId(), "resultSetId");
        requireNonNull(nonNullResultSet.getCreatedAt(), "createdAt");
        requireNonNull(nonNullResultSet.getExpiresAt(), "expiresAt");
        if (!nonNullResultSet.getExpiresAt().isAfter(nonNullResultSet.getCreatedAt())) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
        requireNonNull(nonNullResultSet.getRequest(), "request");
        requireNonNull(nonNullResultSet.getTopologyVersion(), "topologyVersion");
        requireNonBlank(nonNullResultSet.getQuarantineSnapshotId(), "quarantineSnapshotId");
        requireNonNull(nonNullResultSet.getScenarioBundle(), "scenarioBundle");
        validateScenarioBundleBinding(nonNullResultSet);
        validateDensityCalibrationReport(
                nonNullResultSet.getCandidateDensityCalibrationReport(),
                nonNullResultSet.getAlternatives()
        );
        validateSelection("expectedRoute", nonNullResultSet.getExpectedRoute());
        validateSelection("robustRoute", nonNullResultSet.getRobustRoute());
        validateAggregateOnlyFlags(
                nonNullResultSet.getCandidateDensityCalibrationReport(),
                nonNullResultSet.getExpectedRoute(),
                nonNullResultSet.getRobustRoute()
        );
        List<ScenarioRouteSelection> alternatives = nonNullResultSet.getAlternatives();
        if (alternatives != null) {
            for (int i = 0; i < alternatives.size(); i++) {
                validateSelection("alternatives[" + i + "]", alternatives.get(i));
            }
        }
    }

    private static void validateScenarioBundleBinding(FutureRouteResultSet resultSet) {
        ScenarioBundle scenarioBundle = resultSet.getScenarioBundle();
        if (!Objects.equals(scenarioBundle.getTopologyVersion(), resultSet.getTopologyVersion())) {
            throw new IllegalArgumentException("scenarioBundle topologyVersion must match route result topologyVersion");
        }
        if (!Objects.equals(scenarioBundle.getQuarantineSnapshotId(), resultSet.getQuarantineSnapshotId())) {
            throw new IllegalArgumentException("scenarioBundle quarantineSnapshotId must match route result quarantineSnapshotId");
        }
        Instant expiresAt = resultSet.getExpiresAt();
        if (scenarioBundle.getValidUntil() == null || scenarioBundle.getValidUntil().isAfter(expiresAt)) {
            throw new IllegalArgumentException("scenarioBundle validUntil must not exceed route result expiry");
        }
    }

    private static void validateDensityCalibrationReport(
            CandidateDensityCalibrationReport report,
            List<ScenarioRouteSelection> alternatives
    ) {
        CandidateDensityCalibrationReport nonNullReport = Objects.requireNonNull(report, "candidateDensityCalibrationReport");
        requireNonBlank(nonNullReport.getPolicyId(), "candidateDensityCalibrationReport.policyId");
        if (nonNullReport.getScenarioCount() <= 0) {
            throw new IllegalArgumentException("candidateDensityCalibrationReport scenarioCount must be > 0");
        }
        if (nonNullReport.getScenarioOptimalRouteCount() < 0) {
            throw new IllegalArgumentException("candidateDensityCalibrationReport scenarioOptimalRouteCount must be >= 0");
        }
        if (nonNullReport.getUniqueScenarioOptimalRouteCount() < 0) {
            throw new IllegalArgumentException("candidateDensityCalibrationReport uniqueScenarioOptimalRouteCount must be >= 0");
        }
        if (nonNullReport.getUniqueScenarioOptimalRouteCount() > nonNullReport.getScenarioOptimalRouteCount()) {
            throw new IllegalArgumentException(
                    "candidateDensityCalibrationReport uniqueScenarioOptimalRouteCount must be <= scenarioOptimalRouteCount"
            );
        }
        if (nonNullReport.getUniqueCandidateRouteCount() < nonNullReport.getUniqueScenarioOptimalRouteCount()) {
            throw new IllegalArgumentException(
                    "candidateDensityCalibrationReport uniqueCandidateRouteCount must be >= uniqueScenarioOptimalRouteCount"
            );
        }
        int expectedAggregateAdded = nonNullReport.getUniqueCandidateRouteCount() - nonNullReport.getUniqueScenarioOptimalRouteCount();
        if (nonNullReport.getAggregateAddedCandidateCount() != expectedAggregateAdded) {
            throw new IllegalArgumentException(
                    "candidateDensityCalibrationReport aggregateAddedCandidateCount must match candidate expansion delta"
            );
        }
        int selectedAlternativeCount = alternatives == null ? 0 : alternatives.size();
        if (nonNullReport.getSelectedAlternativeCount() != selectedAlternativeCount) {
            throw new IllegalArgumentException(
                    "candidateDensityCalibrationReport selectedAlternativeCount must match retained alternative count"
            );
        }
        double expectedScenarioCoverageRatio = nonNullReport.getScenarioOptimalRouteCount() == 0
                ? 0.0d
                : (double) nonNullReport.getUniqueScenarioOptimalRouteCount()
                / (double) nonNullReport.getScenarioOptimalRouteCount();
        double expectedCandidateCoverageRatio = nonNullReport.getUniqueScenarioOptimalRouteCount() == 0
                ? (nonNullReport.getUniqueCandidateRouteCount() == 0 ? 0.0d : Double.NaN)
                : (double) nonNullReport.getUniqueCandidateRouteCount()
                / (double) nonNullReport.getUniqueScenarioOptimalRouteCount();
        double expectedAggregateExpansionRatio = nonNullReport.getUniqueScenarioOptimalRouteCount() == 0
                ? (nonNullReport.getAggregateAddedCandidateCount() == 0 ? 0.0d : Double.NaN)
                : (double) nonNullReport.getAggregateAddedCandidateCount()
                / (double) nonNullReport.getUniqueScenarioOptimalRouteCount();
        validateBoundedRatio(
                "candidateDensityCalibrationReport scenarioCoverageRatio",
                nonNullReport.getScenarioCoverageRatio(),
                0.0d,
                1.0d
        );
        validateNonNegativeRatio(
                "candidateDensityCalibrationReport candidateCoverageRatio",
                nonNullReport.getCandidateCoverageRatio()
        );
        validateNonNegativeRatio(
                "candidateDensityCalibrationReport aggregateExpansionRatio",
                nonNullReport.getAggregateExpansionRatio()
        );
        requireApproximately(
                "candidateDensityCalibrationReport scenarioCoverageRatio",
                nonNullReport.getScenarioCoverageRatio(),
                expectedScenarioCoverageRatio
        );
        requireApproximately(
                "candidateDensityCalibrationReport candidateCoverageRatio",
                nonNullReport.getCandidateCoverageRatio(),
                expectedCandidateCoverageRatio
        );
        requireApproximately(
                "candidateDensityCalibrationReport aggregateExpansionRatio",
                nonNullReport.getAggregateExpansionRatio(),
                expectedAggregateExpansionRatio
        );
        if (nonNullReport.getDensityClass() == null) {
            throw new IllegalArgumentException("candidateDensityCalibrationReport densityClass must be non-null");
        }
        CandidateDensityClass expectedDensityClass = expectedDensityClass(
                nonNullReport.getUniqueCandidateRouteCount(),
                nonNullReport.getAggregateAddedCandidateCount(),
                nonNullReport.getScenarioCoverageRatio()
        );
        if (nonNullReport.getDensityClass() != expectedDensityClass) {
            throw new IllegalArgumentException("candidateDensityCalibrationReport densityClass is inconsistent with coverage metrics");
        }
    }

    private static CandidateDensityClass expectedDensityClass(
            int uniqueCandidateRouteCount,
            int aggregateAddedCandidateCount,
            double scenarioCoverageRatio
    ) {
        if (uniqueCandidateRouteCount <= 1) {
            return CandidateDensityClass.LOW_DENSITY;
        }
        if (aggregateAddedCandidateCount > 0) {
            return CandidateDensityClass.HIGH_DENSITY;
        }
        return scenarioCoverageRatio >= 0.75d
                ? CandidateDensityClass.HIGH_DENSITY
                : CandidateDensityClass.LOW_DENSITY;
    }

    private static void validateSelection(String fieldName, ScenarioRouteSelection selection) {
        ScenarioRouteSelection nonNullSelection = Objects.requireNonNull(selection, fieldName);
        if (nonNullSelection.getRoute() == null) {
            throw new IllegalArgumentException(fieldName + ".route must be non-null");
        }
        if (nonNullSelection.getRouteSelectionProvenance() == null) {
            throw new IllegalArgumentException(fieldName + ".routeSelectionProvenance must be non-null");
        }
        if (nonNullSelection.getRoute().isReachable()) {
            if (nonNullSelection.getRouteSelectionProvenance() == RouteSelectionProvenance.UNREACHABLE) {
                throw new IllegalArgumentException(fieldName + " reachable route cannot use UNREACHABLE provenance");
            }
        } else if (nonNullSelection.getRouteSelectionProvenance() != RouteSelectionProvenance.UNREACHABLE) {
            throw new IllegalArgumentException(fieldName + " unreachable route must use UNREACHABLE provenance");
        }
    }

    private static void validateAggregateOnlyFlags(
            CandidateDensityCalibrationReport report,
            ScenarioRouteSelection expectedRoute,
            ScenarioRouteSelection robustRoute
    ) {
        boolean expectedAggregateOnly = expectedRoute.getRouteSelectionProvenance() == RouteSelectionProvenance.AGGREGATE_OBJECTIVE;
        boolean robustAggregateOnly = robustRoute.getRouteSelectionProvenance() == RouteSelectionProvenance.AGGREGATE_OBJECTIVE;
        if (report.isExpectedRouteAggregateOnly() != expectedAggregateOnly) {
            throw new IllegalArgumentException(
                    "candidateDensityCalibrationReport expectedRouteAggregateOnly must match expectedRoute provenance"
            );
        }
        if (report.isRobustRouteAggregateOnly() != robustAggregateOnly) {
            throw new IllegalArgumentException(
                    "candidateDensityCalibrationReport robustRouteAggregateOnly must match robustRoute provenance"
            );
        }
    }

    private static void validateBoundedRatio(String fieldName, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(fieldName + " must be finite within [" + min + ", " + max + "]");
        }
    }

    private static void validateNonNegativeRatio(String fieldName, double value) {
        if (!Double.isFinite(value) || value < 0.0d) {
            throw new IllegalArgumentException(fieldName + " must be finite and >= 0");
        }
    }

    private static void requireApproximately(String fieldName, double actual, double expected) {
        if (Double.isNaN(expected) || Math.abs(actual - expected) > PROBABILITY_TOLERANCE) {
            throw new IllegalArgumentException(fieldName + " does not match derived calibration metrics");
        }
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be non-blank");
        }
    }

    private static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must be non-null");
        }
    }
}
