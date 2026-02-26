package org.Aayush.routing.traits.addressing;

import org.Aayush.core.id.IDMapper;
import org.Aayush.routing.core.MatrixRequest;
import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.core.RouteCoreException;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.spatial.SpatialMatch;
import org.Aayush.routing.spatial.SpatialRuntime;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Stage 15 addressing normalization engine.
 *
 * <p>The engine resolves typed and legacy endpoint fields into internal node anchors,
 * enforces deterministic validation/reason-code contracts, and emits reverse-mapped
 * endpoint metadata for response wiring.</p>
 */
public final class AddressingTraitEngine {
    private static final int COORDINATE_CACHE_MAX_ENTRIES = 16_384;
    private static final int COORDINATE_CACHE_SEGMENT_COUNT = 16;

    private final SegmentedCoordinateResolutionCache coordinateResolutionCache;

    public AddressingTraitEngine() {
        this(COORDINATE_CACHE_MAX_ENTRIES, COORDINATE_CACHE_SEGMENT_COUNT);
    }

    AddressingTraitEngine(int coordinateCacheMaxEntries, int coordinateCacheSegmentCount) {
        this.coordinateResolutionCache = new SegmentedCoordinateResolutionCache(
                coordinateCacheMaxEntries,
                coordinateCacheSegmentCount
        );
    }

    /**
     * Resolves and validates one route request addressing payload.
     */
    public RouteResolution resolveRoute(RouteRequest request, ResolveContext context) {
        Objects.requireNonNull(request, "request");
        ResolveContext nonNullContext = Objects.requireNonNull(context, "context");

        long startNanos = System.nanoTime();
        AddressSlot sourceSlot = routeSlot(
                request.getSourceAddress(),
                request.getSourceExternalId(),
                RouteCore.REASON_SOURCE_EXTERNAL_ID_REQUIRED,
                "sourceAddress",
                "sourceExternalId"
        );
        AddressSlot targetSlot = routeSlot(
                request.getTargetAddress(),
                request.getTargetExternalId(),
                RouteCore.REASON_TARGET_EXTERNAL_ID_REQUIRED,
                "targetAddress",
                "targetExternalId"
        );

        boolean mixedMode = sourceSlot.input().getType() != targetSlot.input().getType();
        if (mixedMode && !Boolean.TRUE.equals(request.getAllowMixedAddressing())) {
            throw new RouteCoreException(
                    RouteCore.REASON_MIXED_MODE_DISABLED,
                    "mixed address types require allowMixedAddressing=true"
            );
        }

        AddressingTrait trait = nonNullContext.runtimeBinding().getAddressingTrait();
        validateRequestTraitHint(
                request.getAddressingTraitId(),
                trait,
                nonNullContext.traitCatalog()
        );
        StrategySelection strategySelection = resolveCoordinateStrategy(
                request.getCoordinateDistanceStrategyId(),
                request.getMaxSnapDistance(),
                collectCoordinateInputs(List.of(sourceSlot, targetSlot)),
                nonNullContext.coordinateStrategyRegistry(),
                nonNullContext.addressingPolicy()
        );

        MutableCounters counters = new MutableCounters();
        LinkedHashMap<EndpointKey, ResolvedAddress> cache = new LinkedHashMap<>();
        ResolvedAddress sourceResolved = resolveCached(sourceSlot, trait, strategySelection, nonNullContext, counters, cache);
        ResolvedAddress targetResolved = resolveCached(targetSlot, trait, strategySelection, nonNullContext, counters, cache);

        AddressingTelemetry telemetry = counters.toTelemetry(
                2,
                cache.size(),
                System.nanoTime() - startNanos,
                mixedMode
        );

        return new RouteResolution(
                sourceResolved.getInternalNodeId(),
                targetResolved.getInternalNodeId(),
                sourceResolved,
                targetResolved,
                telemetry
        );
    }

    /**
     * Resolves and validates one matrix request addressing payload.
     */
    public MatrixResolution resolveMatrix(MatrixRequest request, ResolveContext context) {
        Objects.requireNonNull(request, "request");
        ResolveContext nonNullContext = Objects.requireNonNull(context, "context");

        long startNanos = System.nanoTime();
        List<AddressSlot> sourceSlots = matrixSlots(
                nullSafeAddressList(request.getSourceAddresses()),
                nullSafeStringList(request.getSourceExternalIds()),
                RouteCore.REASON_SOURCE_LIST_REQUIRED,
                RouteCore.REASON_SOURCE_EXTERNAL_ID_REQUIRED,
                "sourceAddresses",
                "sourceExternalIds"
        );
        List<AddressSlot> targetSlots = matrixSlots(
                nullSafeAddressList(request.getTargetAddresses()),
                nullSafeStringList(request.getTargetExternalIds()),
                RouteCore.REASON_TARGET_LIST_REQUIRED,
                RouteCore.REASON_TARGET_EXTERNAL_ID_REQUIRED,
                "targetAddresses",
                "targetExternalIds"
        );

        boolean hasExternal = false;
        boolean hasCoordinates = false;
        for (AddressSlot slot : sourceSlots) {
            if (slot.input().getType() == AddressType.EXTERNAL_ID) {
                hasExternal = true;
            } else {
                hasCoordinates = true;
            }
        }
        for (AddressSlot slot : targetSlots) {
            if (slot.input().getType() == AddressType.EXTERNAL_ID) {
                hasExternal = true;
            } else {
                hasCoordinates = true;
            }
        }

        boolean mixedMode = hasExternal && hasCoordinates;
        if (mixedMode && !Boolean.TRUE.equals(request.getAllowMixedAddressing())) {
            throw new RouteCoreException(
                RouteCore.REASON_MIXED_MODE_DISABLED,
                "mixed address types require allowMixedAddressing=true"
            );
        }

        AddressingTrait trait = nonNullContext.runtimeBinding().getAddressingTrait();
        validateRequestTraitHint(
                request.getAddressingTraitId(),
                trait,
                nonNullContext.traitCatalog()
        );
        List<AddressInput> coordinateInputs = collectCoordinateInputs(sourceSlots, targetSlots);
        StrategySelection strategySelection = resolveCoordinateStrategy(
                request.getCoordinateDistanceStrategyId(),
                request.getMaxSnapDistance(),
                coordinateInputs,
                nonNullContext.coordinateStrategyRegistry(),
                nonNullContext.addressingPolicy()
        );

        MutableCounters counters = new MutableCounters();
        LinkedHashMap<EndpointKey, ResolvedAddress> cache = new LinkedHashMap<>();

        int[] sourceNodeIds = new int[sourceSlots.size()];
        List<String> sourceExternalIds = new ArrayList<>(sourceSlots.size());
        for (int i = 0; i < sourceSlots.size(); i++) {
            ResolvedAddress resolved = resolveCached(sourceSlots.get(i), trait, strategySelection, nonNullContext, counters, cache);
            sourceNodeIds[i] = resolved.getInternalNodeId();
            sourceExternalIds.add(resolved.getResolvedExternalId());
        }

        int[] targetNodeIds = new int[targetSlots.size()];
        List<String> targetExternalIds = new ArrayList<>(targetSlots.size());
        for (int i = 0; i < targetSlots.size(); i++) {
            ResolvedAddress resolved = resolveCached(targetSlots.get(i), trait, strategySelection, nonNullContext, counters, cache);
            targetNodeIds[i] = resolved.getInternalNodeId();
            targetExternalIds.add(resolved.getResolvedExternalId());
        }

        AddressingTelemetry telemetry = counters.toTelemetry(
                sourceSlots.size() + targetSlots.size(),
                cache.size(),
                System.nanoTime() - startNanos,
                mixedMode
        );

        return new MatrixResolution(
                sourceNodeIds,
                targetNodeIds,
                sourceExternalIds,
                targetExternalIds,
                telemetry
        );
    }

    private AddressSlot routeSlot(
            AddressInput typedInput,
            String legacyExternalId,
            String requiredReasonCode,
            String typedFieldName,
            String legacyFieldName
    ) {
        if (typedInput != null && legacyExternalId != null) {
            throw new RouteCoreException(
                    RouteCore.REASON_TYPED_LEGACY_AMBIGUITY,
                    "cannot provide both " + typedFieldName + " and " + legacyFieldName
            );
        }
        if (typedInput != null) {
            return new AddressSlot(typedInput, true, null, typedFieldName);
        }
        if (legacyExternalId == null || legacyExternalId.isBlank()) {
            throw new RouteCoreException(requiredReasonCode, legacyFieldName + " must be non-blank");
        }
        return new AddressSlot(AddressInput.ofExternalId(legacyExternalId), false, requiredReasonCode, legacyFieldName);
    }

    private List<AddressSlot> matrixSlots(
            List<AddressInput> typedInputs,
            List<String> legacyExternalIds,
            String listRequiredReasonCode,
            String entryRequiredReasonCode,
            String typedFieldName,
            String legacyFieldName
    ) {
        boolean typedPresent = typedInputs != null && !typedInputs.isEmpty();
        boolean legacyPresent = legacyExternalIds != null && !legacyExternalIds.isEmpty();

        if (typedPresent && legacyPresent) {
            throw new RouteCoreException(
                    RouteCore.REASON_TYPED_LEGACY_AMBIGUITY,
                    "cannot provide both " + typedFieldName + " and " + legacyFieldName
            );
        }
        if (!typedPresent && !legacyPresent) {
            throw new RouteCoreException(listRequiredReasonCode, legacyFieldName + " must be non-empty");
        }

        ArrayList<AddressSlot> slots = new ArrayList<>();
        if (typedPresent) {
            for (int i = 0; i < typedInputs.size(); i++) {
                AddressInput input = typedInputs.get(i);
                if (input == null) {
                    throw new RouteCoreException(
                            RouteCore.REASON_MALFORMED_TYPED_PAYLOAD,
                            typedFieldName + "[" + i + "] must be non-null"
                    );
                }
                slots.add(new AddressSlot(input, true, null, typedFieldName + "[" + i + "]"));
            }
            return List.copyOf(slots);
        }

        for (int i = 0; i < legacyExternalIds.size(); i++) {
            String externalId = legacyExternalIds.get(i);
            if (externalId == null || externalId.isBlank()) {
                throw new RouteCoreException(
                        entryRequiredReasonCode,
                        legacyFieldName + "[" + i + "] must be non-blank"
                );
            }
            slots.add(new AddressSlot(
                    AddressInput.ofExternalId(externalId),
                    false,
                    entryRequiredReasonCode,
                    legacyFieldName + "[" + i + "]"
            ));
        }
        return List.copyOf(slots);
    }

    private static List<AddressInput> collectCoordinateInputs(List<AddressSlot> first, List<AddressSlot> second) {
        ArrayList<AddressInput> coordinates = new ArrayList<>();
        for (AddressSlot slot : first) {
            if (slot.input().getType() == AddressType.COORDINATES) {
                coordinates.add(slot.input());
            }
        }
        for (AddressSlot slot : second) {
            if (slot.input().getType() == AddressType.COORDINATES) {
                coordinates.add(slot.input());
            }
        }
        return List.copyOf(coordinates);
    }

    private static List<AddressInput> collectCoordinateInputs(List<AddressSlot> slots) {
        ArrayList<AddressInput> coordinates = new ArrayList<>();
        for (AddressSlot slot : slots) {
            if (slot.input().getType() == AddressType.COORDINATES) {
                coordinates.add(slot.input());
            }
        }
        return List.copyOf(coordinates);
    }

    private void validateRequestTraitHint(
            String requestedTraitId,
            AddressingTrait runtimeTrait,
            AddressingTraitCatalog catalog
    ) {
        String traitId = normalizeOptionalId(requestedTraitId);
        if (traitId == null) {
            return;
        }
        AddressingTrait requestedTrait = catalog.trait(traitId);
        if (requestedTrait == null) {
            throw new RouteCoreException(
                    RouteCore.REASON_UNKNOWN_ADDRESSING_TRAIT,
                    "unknown addressing trait id: " + traitId
            );
        }
        if (!traitId.equals(runtimeTrait.id())) {
            throw new RouteCoreException(
                    RouteCore.REASON_ADDRESSING_RUNTIME_MISMATCH,
                    "request addressingTraitId " + traitId
                            + " does not match startup trait " + runtimeTrait.id()
            );
        }
    }

    private StrategySelection resolveCoordinateStrategy(
            String requestedStrategyId,
            Double requestMaxSnapDistance,
            List<AddressInput> coordinateInputs,
            CoordinateStrategyRegistry registry,
            AddressingPolicy policy
    ) {
        if (coordinateInputs.isEmpty()) {
            return null;
        }

        String strategyId = normalizeOptionalId(requestedStrategyId);
        if (strategyId == null) {
            throw new RouteCoreException(
                    RouteCore.REASON_COORDINATE_STRATEGY_REQUIRED,
                    "coordinateDistanceStrategyId must be provided for coordinate inputs"
            );
        }

        CoordinateDistanceStrategy strategy = registry.strategy(strategyId);
        if (strategy == null) {
            throw new RouteCoreException(
                    RouteCore.REASON_UNKNOWN_COORDINATE_STRATEGY,
                    "unknown coordinateDistanceStrategyId: " + strategyId
            );
        }

        for (AddressInput input : coordinateInputs) {
            String hint = normalizeOptionalId(input.getCoordinateStrategyHintId());
            if (hint != null && !hint.equals(strategyId)) {
                throw new RouteCoreException(
                        RouteCore.REASON_MALFORMED_TYPED_PAYLOAD,
                        "coordinate strategy hint mismatch: expected " + strategyId + ", got " + hint
                );
            }
        }

        final double maxSnapDistance;
        try {
            maxSnapDistance = policy.resolveMaxSnapDistance(strategyId, requestMaxSnapDistance, strategy);
        } catch (IllegalArgumentException ex) {
            throw new RouteCoreException(
                    RouteCore.REASON_INVALID_MAX_SNAP_DISTANCE,
                    ex.getMessage(),
                    ex
            );
        }

        return new StrategySelection(strategyId, strategy, maxSnapDistance);
    }

    private ResolvedAddress resolveCached(
            AddressSlot slot,
            AddressingTrait trait,
            StrategySelection strategySelection,
            ResolveContext context,
            MutableCounters counters,
            LinkedHashMap<EndpointKey, ResolvedAddress> cache
    ) {
        EndpointKey key = EndpointKey.from(slot.input(), strategySelection);
        ResolvedAddress cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        ResolvedAddress resolved = resolveOne(slot, trait, strategySelection, context, counters);
        cache.put(key, resolved);
        counters.resolveCalls++;
        return resolved;
    }

    private ResolvedAddress resolveOne(
            AddressSlot slot,
            AddressingTrait trait,
            StrategySelection strategySelection,
            ResolveContext context,
            MutableCounters counters
    ) {
        AddressInput input = slot.input();
        if (input == null || input.getType() == null) {
            throw new RouteCoreException(
                    RouteCore.REASON_MALFORMED_TYPED_PAYLOAD,
                    slot.fieldName() + " must provide a valid address type"
            );
        }
        if (!trait.supports(input.getType())) {
            throw new RouteCoreException(
                    RouteCore.REASON_UNSUPPORTED_ADDRESS_TYPE,
                    "trait " + trait.id() + " does not support address type " + input.getType()
            );
        }

        if (input.getType() == AddressType.EXTERNAL_ID) {
            if (input.getCoordinateFirst() != null
                    || input.getCoordinateSecond() != null
                    || input.getCoordinateStrategyHintId() != null) {
                throw new RouteCoreException(
                        RouteCore.REASON_MALFORMED_TYPED_PAYLOAD,
                        slot.fieldName() + " EXTERNAL_ID payload must not include coordinate fields"
                );
            }
        } else if (input.getExternalId() != null) {
            throw new RouteCoreException(
                    RouteCore.REASON_MALFORMED_TYPED_PAYLOAD,
                    slot.fieldName() + " COORDINATES payload must not include externalId"
            );
        }

        return switch (input.getType()) {
            case EXTERNAL_ID -> resolveExternal(slot, trait, context, counters);
            case COORDINATES -> resolveCoordinates(slot, trait, strategySelection, context, counters);
        };
    }

    private ResolvedAddress resolveExternal(
            AddressSlot slot,
            AddressingTrait trait,
            ResolveContext context,
            MutableCounters counters
    ) {
        counters.externalIdResolveCount++;
        String externalId = slot.input().getExternalId();
        if (externalId == null || externalId.isBlank()) {
            if (slot.requiredReasonCode() != null) {
                throw new RouteCoreException(slot.requiredReasonCode(), slot.fieldName() + " must be non-blank");
            }
            throw new RouteCoreException(
                    RouteCore.REASON_MALFORMED_TYPED_PAYLOAD,
                    slot.fieldName() + " must provide non-blank externalId"
            );
        }

        String unknownExternalReasonCode = slot.typedField()
                ? RouteCore.REASON_UNKNOWN_TYPED_EXTERNAL_NODE
                : RouteCore.REASON_UNKNOWN_EXTERNAL_NODE;
        int internalNodeId = mapExternalToInternal(externalId, context, unknownExternalReasonCode);
        String resolvedExternalId = mapInternalToExternal(internalNodeId, context.nodeIdMapper());

        return ResolvedAddress.builder()
                .inputType(AddressType.EXTERNAL_ID)
                .addressingTraitId(trait.id())
                .internalNodeId(internalNodeId)
                .resolvedExternalId(resolvedExternalId)
                .inputExternalId(externalId)
                .build();
    }

    private ResolvedAddress resolveCoordinates(
            AddressSlot slot,
            AddressingTrait trait,
            StrategySelection strategySelection,
            ResolveContext context,
            MutableCounters counters
    ) {
        counters.coordinateResolveCount++;
        if (strategySelection == null) {
            throw new RouteCoreException(
                    RouteCore.REASON_COORDINATE_STRATEGY_REQUIRED,
                    "coordinateDistanceStrategyId must be provided for coordinate inputs"
            );
        }

        Double firstValue = slot.input().getCoordinateFirst();
        Double secondValue = slot.input().getCoordinateSecond();
        if (firstValue == null || secondValue == null) {
            throw new RouteCoreException(
                    RouteCore.REASON_MALFORMED_TYPED_PAYLOAD,
                    slot.fieldName() + " must provide both coordinate values"
            );
        }

        double first = firstValue;
        double second = secondValue;
        if (!Double.isFinite(first) || !Double.isFinite(second)) {
            throw new RouteCoreException(
                    RouteCore.REASON_NON_FINITE_COORDINATES,
                    slot.fieldName() + " coordinates must be finite"
            );
        }

        try {
            strategySelection.strategy().validate(first, second);
        } catch (CoordinateDistanceStrategy.CoordinateValidationException ex) {
            throw new RouteCoreException(ex.getReasonCode(), ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw new RouteCoreException(
                    RouteCore.REASON_COORDINATE_STRATEGY_FAILURE,
                    "coordinate strategy validation failed for "
                            + strategySelection.strategyId()
                            + ": "
                            + ex.getMessage(),
                    ex
            );
        }

        SpatialRuntime runtime = context.spatialRuntime();
        if (runtime == null || !runtime.enabled()) {
            throw new RouteCoreException(
                    RouteCore.REASON_SPATIAL_RUNTIME_UNAVAILABLE,
                    "spatial runtime is required for coordinate-based addressing"
            );
        }

        CoordinateResolutionCacheKey cacheKey = CoordinateResolutionCacheKey.of(
                context.edgeGraph(),
                context.nodeIdMapper(),
                runtime,
                trait.id(),
                strategySelection.strategyId(),
                strategySelection.maxSnapDistance(),
                first,
                second
        );
        ResolvedAddress cached = coordinateResolutionCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        final SpatialMatch match;
        try {
            match = runtime.nearest(first, second);
        } catch (IllegalStateException ex) {
            throw new RouteCoreException(
                    RouteCore.REASON_SPATIAL_RUNTIME_UNAVAILABLE,
                    "spatial runtime is disabled",
                    ex
            );
        } catch (IllegalArgumentException ex) {
            throw new RouteCoreException(
                    RouteCore.REASON_NON_FINITE_COORDINATES,
                    ex.getMessage(),
                    ex
            );
        }

        final double snapDistance;
        try {
            snapDistance = strategySelection.strategy().distance(
                    first,
                    second,
                    match.nodeX(),
                    match.nodeY()
            );
        } catch (RuntimeException ex) {
            throw new RouteCoreException(
                    RouteCore.REASON_COORDINATE_STRATEGY_FAILURE,
                    "coordinate strategy distance failed for "
                            + strategySelection.strategyId()
                            + ": "
                            + ex.getMessage(),
                    ex
            );
        }
        if (!Double.isFinite(snapDistance)) {
            throw new RouteCoreException(
                    RouteCore.REASON_NON_FINITE_COORDINATES,
                    "computed snap distance is non-finite"
            );
        }
        if (snapDistance < 0.0d) {
            throw new RouteCoreException(
                    RouteCore.REASON_COORDINATE_STRATEGY_FAILURE,
                    "coordinate strategy distance failed for "
                            + strategySelection.strategyId()
                            + ": computed snap distance must be >= 0, got " + snapDistance
            );
        }

        if (snapDistance > strategySelection.maxSnapDistance()) {
            counters.snapThresholdRejectCount++;
            throw new RouteCoreException(
                    RouteCore.REASON_SNAP_THRESHOLD_EXCEEDED,
                    "snap distance " + snapDistance
                            + " exceeds maxSnapDistance " + strategySelection.maxSnapDistance()
            );
        }

        int internalNodeId = match.nodeId();
        if (internalNodeId < 0 || internalNodeId >= context.edgeGraph().nodeCount()) {
            throw new RouteCoreException(
                    RouteCore.REASON_INTERNAL_NODE_OUT_OF_BOUNDS,
                    "spatial match internal node id out of bounds: " + internalNodeId
            );
        }
        String resolvedExternalId = mapInternalToExternal(internalNodeId, context.nodeIdMapper());

        ResolvedAddress resolved = ResolvedAddress.builder()
                .inputType(AddressType.COORDINATES)
                .addressingTraitId(trait.id())
                .internalNodeId(internalNodeId)
                .resolvedExternalId(resolvedExternalId)
                .inputCoordinateFirst(first)
                .inputCoordinateSecond(second)
                .coordinateDistanceStrategyId(strategySelection.strategyId())
                .snapDistance(snapDistance)
                .build();
        return cacheCoordinateResolution(cacheKey, resolved);
    }

    private ResolvedAddress cacheCoordinateResolution(
            CoordinateResolutionCacheKey cacheKey,
            ResolvedAddress resolved
    ) {
        return coordinateResolutionCache.putIfAbsent(cacheKey, resolved);
    }

    private int mapExternalToInternal(String externalId, ResolveContext context, String unknownExternalReasonCode) {
        final int internalNodeId;
        try {
            internalNodeId = context.nodeIdMapper().toInternal(externalId);
        } catch (IDMapper.UnknownIDException ex) {
            throw new RouteCoreException(
                    unknownExternalReasonCode,
                    "unknown external node id: " + externalId,
                    ex
            );
        }

        if (internalNodeId < 0 || internalNodeId >= context.edgeGraph().nodeCount()) {
            throw new RouteCoreException(
                    RouteCore.REASON_INTERNAL_NODE_OUT_OF_BOUNDS,
                    "mapped internal node id out of range for " + externalId + ": " + internalNodeId
            );
        }
        return internalNodeId;
    }

    private String mapInternalToExternal(int internalNodeId, IDMapper mapper) {
        try {
            return mapper.toExternal(internalNodeId);
        } catch (RuntimeException ex) {
            throw new RouteCoreException(
                    RouteCore.REASON_EXTERNAL_MAPPING_FAILED,
                    "failed to map internal node " + internalNodeId + " to external id",
                    ex
            );
        }
    }

    private List<String> nullSafeStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values;
    }

    private List<AddressInput> nullSafeAddressList(List<AddressInput> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values;
    }

    private static String normalizeOptionalId(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * Route-addressing normalization output.
     */
    public record RouteResolution(
            int sourceNodeId,
            int targetNodeId,
            ResolvedAddress sourceResolvedAddress,
            ResolvedAddress targetResolvedAddress,
            AddressingTelemetry telemetry
    ) {
    }

    /**
     * Matrix-addressing normalization output.
     */
    public record MatrixResolution(
            int[] sourceNodeIds,
            int[] targetNodeIds,
            List<String> sourceExternalIds,
            List<String> targetExternalIds,
            AddressingTelemetry telemetry
    ) {
        public MatrixResolution {
            sourceNodeIds = sourceNodeIds.clone();
            targetNodeIds = targetNodeIds.clone();
            sourceExternalIds = List.copyOf(sourceExternalIds);
            targetExternalIds = List.copyOf(targetExternalIds);
            telemetry = telemetry == null ? AddressingTelemetry.empty() : telemetry;
        }
    }

    /**
     * Dependencies required by the addressing normalization engine.
     */
    public record ResolveContext(
            EdgeGraph edgeGraph,
            IDMapper nodeIdMapper,
            SpatialRuntime spatialRuntime,
            AddressingTraitCatalog traitCatalog,
            CoordinateStrategyRegistry coordinateStrategyRegistry,
            AddressingPolicy addressingPolicy,
            AddressingRuntimeBinder.Binding runtimeBinding
    ) {
        public ResolveContext {
            Objects.requireNonNull(edgeGraph, "edgeGraph");
            Objects.requireNonNull(nodeIdMapper, "nodeIdMapper");
            Objects.requireNonNull(traitCatalog, "traitCatalog");
            Objects.requireNonNull(coordinateStrategyRegistry, "coordinateStrategyRegistry");
            Objects.requireNonNull(addressingPolicy, "addressingPolicy");
            Objects.requireNonNull(runtimeBinding, "runtimeBinding");
        }
    }

    private record AddressSlot(
            AddressInput input,
            boolean typedField,
            String requiredReasonCode,
            String fieldName
    ) {
    }

    private record StrategySelection(
            String strategyId,
            CoordinateDistanceStrategy strategy,
            double maxSnapDistance
    ) {
    }

    private record EndpointKey(
            AddressType type,
            String externalId,
            long coordinateFirstBits,
            long coordinateSecondBits,
            boolean hasCoordinateFirst,
            boolean hasCoordinateSecond,
            String coordinateStrategyId
    ) {
        static EndpointKey from(AddressInput input, StrategySelection strategySelection) {
            Double first = input.getCoordinateFirst();
            Double second = input.getCoordinateSecond();
            long firstBits = first == null ? 0L : canonicalCoordinateBits(first);
            long secondBits = second == null ? 0L : canonicalCoordinateBits(second);
            String effectiveStrategyId = input.getType() == AddressType.COORDINATES && strategySelection != null
                    ? strategySelection.strategyId()
                    : null;
            return new EndpointKey(
                    input.getType(),
                    input.getExternalId(),
                    firstBits,
                    secondBits,
                    first != null,
                    second != null,
                    effectiveStrategyId
            );
        }

        private static long canonicalCoordinateBits(double value) {
            if (value == 0.0d) {
                return Double.doubleToLongBits(0.0d);
            }
            return Double.doubleToLongBits(value);
        }
    }

    private record CoordinateResolutionCacheKey(
            EdgeGraph edgeGraph,
            IDMapper nodeIdMapper,
            SpatialRuntime spatialRuntime,
            String addressingTraitId,
            String coordinateStrategyId,
            long maxSnapDistanceBits,
            long coordinateFirstBits,
            long coordinateSecondBits
    ) {
        static CoordinateResolutionCacheKey of(
                EdgeGraph edgeGraph,
                IDMapper nodeIdMapper,
                SpatialRuntime spatialRuntime,
                String addressingTraitId,
                String coordinateStrategyId,
                double maxSnapDistance,
                double coordinateFirst,
                double coordinateSecond
        ) {
            return new CoordinateResolutionCacheKey(
                    edgeGraph,
                    nodeIdMapper,
                    spatialRuntime,
                    addressingTraitId,
                    coordinateStrategyId,
                    Double.doubleToLongBits(maxSnapDistance),
                    canonicalCoordinateBits(coordinateFirst),
                    canonicalCoordinateBits(coordinateSecond)
            );
        }
    }

    private static long canonicalCoordinateBits(double value) {
        if (value == 0.0d) {
            return Double.doubleToLongBits(0.0d);
        }
        return Double.doubleToLongBits(value);
    }

    /**
     * Fixed-capacity segmented LRU cache for coordinate snap resolutions.
     */
    private static final class SegmentedCoordinateResolutionCache {
        private final CoordinateCacheSegment[] segments;
        private final int segmentMask;

        SegmentedCoordinateResolutionCache(int maxEntries, int requestedSegmentCount) {
            int normalizedMaxEntries = Math.max(1, maxEntries);
            int segmentCount = normalizeSegmentCount(requestedSegmentCount, normalizedMaxEntries);
            this.segments = new CoordinateCacheSegment[segmentCount];
            this.segmentMask = segmentCount - 1;

            int entriesPerSegment = normalizedMaxEntries / segmentCount;
            int extraEntries = normalizedMaxEntries % segmentCount;
            for (int i = 0; i < segmentCount; i++) {
                int segmentEntries = entriesPerSegment + (i < extraEntries ? 1 : 0);
                segments[i] = new CoordinateCacheSegment(Math.max(1, segmentEntries));
            }
        }

        ResolvedAddress get(CoordinateResolutionCacheKey key) {
            return segmentFor(key).get(key);
        }

        ResolvedAddress putIfAbsent(CoordinateResolutionCacheKey key, ResolvedAddress value) {
            return segmentFor(key).putIfAbsent(key, value);
        }

        private CoordinateCacheSegment segmentFor(CoordinateResolutionCacheKey key) {
            int hash = key.hashCode();
            hash ^= (hash >>> 16);
            return segments[hash & segmentMask];
        }

        private static int normalizeSegmentCount(int requestedSegmentCount, int maxEntries) {
            int desired = Math.max(1, Math.min(requestedSegmentCount, maxEntries));
            int powerOfTwo = 1;
            while (powerOfTwo < desired) {
                powerOfTwo <<= 1;
            }
            return powerOfTwo;
        }

        private static final class CoordinateCacheSegment {
            private final int maxEntries;
            private final ReentrantLock lock = new ReentrantLock();
            private final LinkedHashMap<CoordinateResolutionCacheKey, ResolvedAddress> entries =
                    new LinkedHashMap<>(16, 0.75f, true);

            CoordinateCacheSegment(int maxEntries) {
                this.maxEntries = Math.max(1, maxEntries);
            }

            ResolvedAddress get(CoordinateResolutionCacheKey key) {
                lock.lock();
                try {
                    return entries.get(key);
                } finally {
                    lock.unlock();
                }
            }

            ResolvedAddress putIfAbsent(CoordinateResolutionCacheKey key, ResolvedAddress value) {
                lock.lock();
                try {
                    ResolvedAddress existing = entries.get(key);
                    if (existing != null) {
                        return existing;
                    }
                    entries.put(key, value);
                    if (entries.size() > maxEntries) {
                        Iterator<Map.Entry<CoordinateResolutionCacheKey, ResolvedAddress>> iterator =
                                entries.entrySet().iterator();
                        if (iterator.hasNext()) {
                            iterator.next();
                            iterator.remove();
                        }
                    }
                    return value;
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    private static final class MutableCounters {
        int resolveCalls;
        int externalIdResolveCount;
        int coordinateResolveCount;
        int snapThresholdRejectCount;

        AddressingTelemetry toTelemetry(int endpointCount, int uniqueEndpointCount, long nanos, boolean mixedMode) {
            int dedupSaved = Math.max(0, endpointCount - uniqueEndpointCount);
            return new AddressingTelemetry(
                    endpointCount,
                    uniqueEndpointCount,
                    resolveCalls,
                    dedupSaved,
                    externalIdResolveCount,
                    coordinateResolveCount,
                    snapThresholdRejectCount,
                    nanos,
                    mixedMode
            );
        }
    }
}
