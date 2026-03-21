package org.Aayush.routing.core;

/**
 * Shared matrix-response and exception mapping helpers used by {@link RouteCore}.
 */
final class RouteCoreMatrixSupport {
    private RouteCoreMatrixSupport() {
    }

    static MatrixResponse buildResponse(
            RequestNormalizer.NormalizedMatrixRequest normalized,
            MatrixPlan matrixPlan
    ) {
        InternalMatrixRequest internalRequest = normalized.getInternalRequest();
        return MatrixResponse.builder()
                .sourceExternalIds(normalized.getSourceExternalIds())
                .targetExternalIds(normalized.getTargetExternalIds())
                .reachable(matrixPlan.reachable())
                .totalCosts(matrixPlan.totalCosts())
                .arrivalTicks(matrixPlan.arrivalTicks())
                .algorithm(internalRequest.algorithm())
                .heuristicType(internalRequest.heuristicType())
                .implementationNote(matrixPlan.implementationNote())
                .build();
    }

    static RouteCoreException remapCompatibilityException(RouteCoreException ex) {
        if (RouteCore.REASON_SEARCH_BUDGET_EXCEEDED.equals(ex.getReasonCode())) {
            return new RouteCoreException(
                    RouteCore.REASON_MATRIX_SEARCH_BUDGET_EXCEEDED,
                    ex.getMessage(),
                    ex
            );
        }
        if (RouteCore.REASON_NUMERIC_SAFETY_BREACH.equals(ex.getReasonCode())) {
            return new RouteCoreException(
                    RouteCore.REASON_MATRIX_NUMERIC_SAFETY_BREACH,
                    ex.getMessage(),
                    ex
            );
        }
        return ex;
    }
}
