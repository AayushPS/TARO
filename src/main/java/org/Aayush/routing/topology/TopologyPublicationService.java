package org.Aayush.routing.topology;

import org.Aayush.core.time.TimeUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * v13 builder-side publication service for batched structural rebuild and atomic reload.
 */
public final class TopologyPublicationService {
    private final AtomicReference<TopologyModelSource> activeSource;
    private final StructuralChangeApplier structuralChangeApplier;
    private final TopologyRuntimeFactory runtimeFactory;
    private final TopologyReloadCoordinator reloadCoordinator;
    private final List<TopologyValidationGate> validationGates;
    private final Clock clock;

    public TopologyPublicationService(
            TopologyModelSource initialSource,
            TopologyRuntimeFactory runtimeFactory,
            TopologyReloadCoordinator reloadCoordinator,
            List<TopologyValidationGate> validationGates
    ) {
        this(initialSource, new StructuralChangeApplier(), runtimeFactory, reloadCoordinator, validationGates, Clock.systemUTC());
    }

    public TopologyPublicationService(
            TopologyModelSource initialSource,
            StructuralChangeApplier structuralChangeApplier,
            TopologyRuntimeFactory runtimeFactory,
            TopologyReloadCoordinator reloadCoordinator,
            List<TopologyValidationGate> validationGates,
            Clock clock
    ) {
        TopologyModelSource nonNullInitialSource = Objects.requireNonNull(initialSource, "initialSource");
        nonNullInitialSource.validate();
        this.activeSource = new AtomicReference<>(nonNullInitialSource);
        this.structuralChangeApplier = Objects.requireNonNull(structuralChangeApplier, "structuralChangeApplier");
        this.runtimeFactory = Objects.requireNonNull(runtimeFactory, "runtimeFactory");
        this.reloadCoordinator = Objects.requireNonNull(reloadCoordinator, "reloadCoordinator");
        this.validationGates = List.copyOf(Objects.requireNonNull(validationGates, "validationGates"));
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public TopologyModelSource currentSource() {
        return activeSource.get();
    }

    public synchronized TopologyPublicationResult publish(StructuralChangeSet changeSet) {
        StructuralChangeSet nonNullChangeSet = Objects.requireNonNull(changeSet, "changeSet");
        TopologyModelSource currentSource = activeSource.get();
        TopologyRuntimeSnapshot currentSnapshot = reloadCoordinator.currentSnapshot();
        TopologyModelSource candidateSource = structuralChangeApplier.apply(currentSource, nonNullChangeSet);

        Instant now = clock.instant();
        TopologyVersion candidateVersion = buildTopologyVersion(candidateSource, nonNullChangeSet, now);
        long publicationTicks = toTicks(now, candidateSource.getEngineTimeUnit());
        TopologyRuntimeSnapshot candidateSnapshot = runtimeFactory.buildSnapshot(
                candidateSource,
                candidateVersion,
                publicationTicks,
                currentSnapshot,
                currentSource
        );
        reloadCoordinator.validateReload(candidateSnapshot);

        TopologyValidationContext validationContext = TopologyValidationContext.builder()
                .currentSnapshot(currentSnapshot)
                .candidateSnapshot(candidateSnapshot)
                .currentSource(currentSource)
                .candidateSource(candidateSource)
                .changeSet(nonNullChangeSet)
                .validatedAt(now)
                .build();
        for (TopologyValidationGate validationGate : validationGates) {
            Objects.requireNonNull(validationGate, "validationGate").validate(validationContext);
        }

        boolean reloaded = nonNullChangeSet.getRolloutPolicy() == StructuralChangeSet.RolloutPolicy.ATOMIC_RELOAD;
        if (reloaded) {
            reloadCoordinator.applyReload(candidateSnapshot);
            activeSource.set(candidateSource);
        }

        return TopologyPublicationResult.builder()
                .topologyVersion(candidateVersion)
                .candidateSnapshot(candidateSnapshot)
                .candidateSource(candidateSource)
                .changeSet(nonNullChangeSet)
                .reloaded(reloaded)
                .build();
    }

    private TopologyVersion buildTopologyVersion(
            TopologyModelSource candidateSource,
            StructuralChangeSet changeSet,
            Instant generatedAt
    ) {
        String changeSetHash = "change-" + Integer.toHexString(changeSet.hashCode());
        String sourceHash = "source-" + Integer.toHexString(candidateSource.hashCode());
        String topologyId = "topology-" + generatedAt.toEpochMilli() + "-" + Integer.toHexString(changeSet.hashCode());
        return TopologyVersion.builder()
                .modelVersion(candidateSource.getModelVersion())
                .topologyVersion(topologyId)
                .generatedAt(generatedAt)
                .sourceDataLineageHash(sourceHash)
                .changeSetHash(changeSetHash)
                .build();
    }

    private long toTicks(Instant instant, TimeUtils.EngineTimeUnit unit) {
        return switch (unit) {
            case SECONDS -> instant.getEpochSecond();
            case MILLISECONDS -> instant.toEpochMilli();
        };
    }
}
