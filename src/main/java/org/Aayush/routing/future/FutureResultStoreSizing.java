package org.Aayush.routing.future;

import org.Aayush.routing.core.MatrixRequest;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.core.RouteResponse;
import org.Aayush.routing.overlay.LiveUpdate;
import org.Aayush.routing.topology.TopologyVersion;
import org.Aayush.routing.traits.addressing.AddressInput;
import org.Aayush.routing.traits.addressing.ResolvedAddress;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Approximate retained-result sizing helpers used for bounded in-memory caches.
 */
final class FutureResultStoreSizing {
    private static final long OBJECT_HEADER_BYTES = 16L;
    private static final long LIST_HEADER_BYTES = 24L;
    private static final long STRING_BASE_BYTES = 40L;

    private FutureResultStoreSizing() {
    }

    static long estimateRouteResultSet(FutureRouteResultSet resultSet) {
        long size = OBJECT_HEADER_BYTES + 10 * Long.BYTES;
        size += string(resultSet.getResultSetId());
        size += instant(resultSet.getCreatedAt());
        size += instant(resultSet.getExpiresAt());
        size += futureRouteRequest(resultSet.getRequest());
        size += topologyVersion(resultSet.getTopologyVersion());
        size += string(resultSet.getQuarantineSnapshotId());
        size += scenarioBundle(resultSet.getScenarioBundle());
        size += scenarioRouteSelection(resultSet.getExpectedRoute());
        size += scenarioRouteSelection(resultSet.getRobustRoute());
        size += listOverhead(resultSet.getAlternatives()) + resultSet.getAlternatives().stream()
                .mapToLong(FutureResultStoreSizing::scenarioRouteSelection)
                .sum();
        size += listOverhead(resultSet.getScenarioResults()) + resultSet.getScenarioResults().stream()
                .mapToLong(FutureResultStoreSizing::futureRouteScenarioResult)
                .sum();
        return size;
    }

    static long estimateMatrixMetadata(FutureMatrixResultSet resultSet) {
        long size = OBJECT_HEADER_BYTES + 8 * Long.BYTES;
        size += string(resultSet.getResultSetId());
        size += instant(resultSet.getCreatedAt());
        size += instant(resultSet.getExpiresAt());
        size += futureMatrixRequest(resultSet.getRequest());
        size += topologyVersion(resultSet.getTopologyVersion());
        size += string(resultSet.getQuarantineSnapshotId());
        size += scenarioBundle(resultSet.getScenarioBundle());
        size += matrixAggregateMetadata(resultSet.getAggregate());
        size += listOverhead(resultSet.getScenarioResults()) + resultSet.getScenarioResults().stream()
                .mapToLong(FutureResultStoreSizing::futureMatrixScenarioMetadata)
                .sum();
        return size;
    }

    static long matrixAggregateMetadata(FutureMatrixAggregate aggregate) {
        if (aggregate == null) {
            return 0L;
        }
        long size = OBJECT_HEADER_BYTES + 8 * Long.BYTES;
        size += stringList(aggregate.getSourceExternalIds());
        size += stringList(aggregate.getTargetExternalIds());
        size += string(aggregate.getAggregationNote());
        return size;
    }

    static long futureMatrixScenarioMetadata(FutureMatrixScenarioResult scenarioResult) {
        if (scenarioResult == null) {
            return 0L;
        }
        long size = OBJECT_HEADER_BYTES + Double.BYTES;
        size += string(scenarioResult.getScenarioId());
        size += string(scenarioResult.getLabel());
        size += stringList(scenarioResult.getExplanationTags());
        size += matrixResponseMetadata(scenarioResult.getMatrix());
        return size;
    }

    static long matrixResponseMetadata(org.Aayush.routing.core.MatrixResponse response) {
        if (response == null) {
            return 0L;
        }
        long size = OBJECT_HEADER_BYTES + 6 * Long.BYTES;
        size += stringList(response.getSourceExternalIds());
        size += stringList(response.getTargetExternalIds());
        size += enumBytes(response.getAlgorithm());
        size += enumBytes(response.getHeuristicType());
        size += string(response.getImplementationNote());
        return size;
    }

    private static long futureRouteRequest(FutureRouteRequest request) {
        if (request == null) {
            return 0L;
        }
        long size = OBJECT_HEADER_BYTES + 3 * Long.BYTES;
        size += routeRequest(request.getRouteRequest());
        size += enumBytes(request.getPreferredObjective());
        size += duration(request.getResultTtl());
        return size;
    }

    private static long futureMatrixRequest(FutureMatrixRequest request) {
        if (request == null) {
            return 0L;
        }
        long size = OBJECT_HEADER_BYTES + 2 * Long.BYTES;
        size += matrixRequest(request.getMatrixRequest());
        size += duration(request.getResultTtl());
        return size;
    }

    private static long routeRequest(RouteRequest request) {
        if (request == null) {
            return 0L;
        }
        long size = OBJECT_HEADER_BYTES + 5 * Long.BYTES;
        size += string(request.getSourceExternalId());
        size += string(request.getTargetExternalId());
        size += addressInput(request.getSourceAddress());
        size += addressInput(request.getTargetAddress());
        size += boolBox(request.getAllowMixedAddressing());
        size += number(request.getMaxSnapDistance());
        return size;
    }

    private static long matrixRequest(MatrixRequest request) {
        if (request == null) {
            return 0L;
        }
        long size = OBJECT_HEADER_BYTES + 5 * Long.BYTES;
        size += stringList(request.getSourceExternalIds());
        size += stringList(request.getTargetExternalIds());
        size += listOverhead(request.getSourceAddresses()) + request.getSourceAddresses().stream()
                .mapToLong(FutureResultStoreSizing::addressInput)
                .sum();
        size += listOverhead(request.getTargetAddresses()) + request.getTargetAddresses().stream()
                .mapToLong(FutureResultStoreSizing::addressInput)
                .sum();
        size += boolBox(request.getAllowMixedAddressing());
        size += number(request.getMaxSnapDistance());
        return size;
    }

    private static long topologyVersion(TopologyVersion topologyVersion) {
        if (topologyVersion == null) {
            return 0L;
        }
        long size = OBJECT_HEADER_BYTES + 4 * Long.BYTES;
        size += string(topologyVersion.getModelVersion());
        size += string(topologyVersion.getTopologyVersion());
        size += instant(topologyVersion.getGeneratedAt());
        size += string(topologyVersion.getSourceDataLineageHash());
        size += string(topologyVersion.getChangeSetHash());
        return size;
    }

    private static long scenarioBundle(ScenarioBundle scenarioBundle) {
        if (scenarioBundle == null) {
            return 0L;
        }
        long size = OBJECT_HEADER_BYTES + 4 * Long.BYTES;
        size += string(scenarioBundle.getScenarioBundleId());
        size += instant(scenarioBundle.getGeneratedAt());
        size += instant(scenarioBundle.getValidUntil());
        size += topologyVersion(scenarioBundle.getTopologyVersion());
        size += string(scenarioBundle.getQuarantineSnapshotId());
        size += listOverhead(scenarioBundle.getScenarios()) + scenarioBundle.getScenarios().stream()
                .mapToLong(FutureResultStoreSizing::scenarioDefinition)
                .sum();
        return size;
    }

    private static long scenarioDefinition(ScenarioDefinition scenarioDefinition) {
        if (scenarioDefinition == null) {
            return 0L;
        }
        long size = OBJECT_HEADER_BYTES + Double.BYTES;
        size += string(scenarioDefinition.getScenarioId());
        size += string(scenarioDefinition.getLabel());
        size += scenarioProbabilityAudit(scenarioDefinition.getProbabilityAudit());
        size += stringList(scenarioDefinition.getExplanationTags());
        size += listOverhead(scenarioDefinition.getLiveUpdates()) + scenarioDefinition.getLiveUpdates().stream()
                .mapToLong(FutureResultStoreSizing::liveUpdate)
                .sum();
        return size;
    }

    private static long scenarioProbabilityAudit(ScenarioProbabilityAudit audit) {
        if (audit == null) {
            return 0L;
        }
        long size = OBJECT_HEADER_BYTES + 4 * Double.BYTES;
        size += string(audit.getPolicyId());
        size += string(audit.getEvidenceSource());
        size += boxedLong(audit.getObservedAtTicks());
        size += boxedLong(audit.getEvidenceAgeTicks());
        return size;
    }

    private static long scenarioRouteSelection(ScenarioRouteSelection selection) {
        if (selection == null) {
            return 0L;
        }
        long size = OBJECT_HEADER_BYTES + 6 * Float.BYTES + 2 * Long.BYTES + Double.BYTES;
        size += routeShape(selection.getRoute());
        size += string(selection.getDominantScenarioId());
        size += string(selection.getDominantScenarioLabel());
        size += stringList(selection.getExplanationTags());
        return size;
    }

    private static long routeShape(RouteShape routeShape) {
        if (routeShape == null) {
            return 0L;
        }
        long size = OBJECT_HEADER_BYTES + Long.BYTES + 1;
        size += enumBytes(routeShape.getAlgorithm());
        size += enumBytes(routeShape.getHeuristicType());
        size += resolvedAddress(routeShape.getSourceResolvedAddress());
        size += resolvedAddress(routeShape.getTargetResolvedAddress());
        size += stringList(routeShape.getPathExternalNodeIds());
        return size;
    }

    private static long futureRouteScenarioResult(FutureRouteScenarioResult scenarioResult) {
        if (scenarioResult == null) {
            return 0L;
        }
        long size = OBJECT_HEADER_BYTES + Double.BYTES;
        size += string(scenarioResult.getScenarioId());
        size += string(scenarioResult.getLabel());
        size += routeResponse(scenarioResult.getRoute());
        size += stringList(scenarioResult.getExplanationTags());
        return size;
    }

    private static long routeResponse(RouteResponse response) {
        if (response == null) {
            return 0L;
        }
        long size = OBJECT_HEADER_BYTES + 3 * Long.BYTES + Float.BYTES + Integer.BYTES + 1;
        size += enumBytes(response.getAlgorithm());
        size += enumBytes(response.getHeuristicType());
        size += resolvedAddress(response.getSourceResolvedAddress());
        size += resolvedAddress(response.getTargetResolvedAddress());
        size += stringList(response.getPathExternalNodeIds());
        return size;
    }

    private static long resolvedAddress(ResolvedAddress resolvedAddress) {
        if (resolvedAddress == null) {
            return 0L;
        }
        long size = OBJECT_HEADER_BYTES + 3 * Integer.BYTES;
        size += enumBytes(resolvedAddress.getInputType());
        size += string(resolvedAddress.getAddressingTraitId());
        size += string(resolvedAddress.getResolvedExternalId());
        size += string(resolvedAddress.getInputExternalId());
        size += number(resolvedAddress.getInputCoordinateFirst());
        size += number(resolvedAddress.getInputCoordinateSecond());
        size += string(resolvedAddress.getCoordinateDistanceStrategyId());
        size += number(resolvedAddress.getSnapDistance());
        return size;
    }

    private static long addressInput(AddressInput input) {
        if (input == null) {
            return 0L;
        }
        long size = OBJECT_HEADER_BYTES + Integer.BYTES;
        size += enumBytes(input.getType());
        size += string(input.getExternalId());
        size += number(input.getCoordinateFirst());
        size += number(input.getCoordinateSecond());
        size += string(input.getCoordinateStrategyHintId());
        return size;
    }

    private static long liveUpdate(LiveUpdate liveUpdate) {
        if (liveUpdate == null) {
            return 0L;
        }
        return OBJECT_HEADER_BYTES + Integer.BYTES + Float.BYTES + Long.BYTES;
    }

    private static long stringList(List<String> values) {
        return listOverhead(values) + values.stream().mapToLong(FutureResultStoreSizing::string).sum();
    }

    private static long listOverhead(List<?> values) {
        return values == null ? 0L : LIST_HEADER_BYTES;
    }

    private static long string(String value) {
        if (value == null) {
            return 0L;
        }
        return STRING_BASE_BYTES + ((long) value.length() * 2L);
    }

    private static long instant(Instant instant) {
        return instant == null ? 0L : OBJECT_HEADER_BYTES + Long.BYTES + Integer.BYTES;
    }

    private static long duration(Duration duration) {
        return duration == null ? 0L : OBJECT_HEADER_BYTES + Long.BYTES + Integer.BYTES;
    }

    private static long enumBytes(Enum<?> value) {
        return value == null ? 0L : Integer.BYTES;
    }

    private static long boolBox(Boolean value) {
        return value == null ? 0L : OBJECT_HEADER_BYTES + 1;
    }

    private static long number(Number value) {
        return value == null ? 0L : OBJECT_HEADER_BYTES + Long.BYTES;
    }

    private static long boxedLong(Long value) {
        return value == null ? 0L : OBJECT_HEADER_BYTES + Long.BYTES;
    }
}
