package org.Aayush.routing.future;

import org.Aayush.core.time.TimeUtils;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.overlay.LiveUpdate;
import org.Aayush.routing.profile.ProfileRecurrenceCalibrationStore;
import org.Aayush.routing.profile.ProfileRecencyCalibrationStore;
import org.Aayush.routing.profile.ProfileStore;
import org.Aayush.routing.topology.FailureQuarantine;
import org.Aayush.routing.topology.TopologyVersion;
import org.Aayush.routing.traits.temporal.ResolvedTemporalContext;
import org.Aayush.routing.traits.temporal.TemporalContextResolver;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Default bounded resolver that turns active quarantines and recurring profile signals
 * into a small shipped scenario bundle.
 */
public final class DefaultScenarioBundleResolver implements ScenarioBundleResolver {
    private static final String EVIDENCE_SOURCE_PROFILE_RECENCY = "profile_recency";
    private static final String EVIDENCE_SOURCE_QUARANTINE = "quarantine";
    private static final double INCIDENT_PERSISTS_PROBABILITY = 0.65d;
    private static final double CLEARING_FAST_PROBABILITY = 0.35d;
    private static final double MIN_RECURRENT_SCENARIO_PROBABILITY = 0.15d;
    private static final double MAX_RECURRENT_SCENARIO_PROBABILITY = 0.75d;
    private static final double PERIODIC_PEAK_MIN_RATIO = 1.10d;
    private static final int MAX_RECURRENT_UPDATES = 64;
    private static final String TAG_PERIODIC_PEAK = "periodic_peak";
    private static final String TAG_PERIODIC_SIGNAL_PRESENT = "periodic_signal_present";
    private static final String TAG_WEAK_PERIODIC_SIGNAL = "weak_periodic_signal";
    private static final String TAG_RECURRING_INCIDENT_RISK = "recurring_incident_risk";
    private static final String TAG_RECURRING_INCIDENT_PEAK = "recurring_incident_peak";
    private static final String TAG_RECURRENT_CONFIDENCE_LOW = "recurrent_confidence_low";
    private static final String TAG_RECURRENT_CONFIDENCE_MEDIUM = "recurrent_confidence_medium";
    private static final String TAG_RECURRENT_CONFIDENCE_HIGH = "recurrent_confidence_high";
    private static final String TAG_RECURRENT_CALIBRATION_EXPLICIT = "recurrent_calibration_explicit";
    private static final String TAG_RECURRENT_CALIBRATION_DERIVED = "recurrent_calibration_derived";
    private static final String TAG_RECURRENT_SIGNAL_REJECTED = "recurrent_signal_rejected";
    private static final String TAG_RECOVERY_EXPECTED = "recovery_expected";

    private final RecencyCalibrationConfig recencyCalibrationConfig;

    public DefaultScenarioBundleResolver() {
        this(RecencyCalibrationConfig.defaults());
    }

    public DefaultScenarioBundleResolver(RecencyCalibrationConfig recencyCalibrationConfig) {
        this.recencyCalibrationConfig = Objects.requireNonNull(recencyCalibrationConfig, "recencyCalibrationConfig");
    }

    @Override
    public ScenarioBundle resolve(
            ScenarioBundleRequest request,
            CostEngine baseCostEngine,
            ResolvedTemporalContext temporalContext,
            TopologyVersion topologyVersion,
            FailureQuarantine.Snapshot quarantineSnapshot,
            Clock clock
    ) {
        Objects.requireNonNull(request, "request");
        CostEngine nonNullBaseCostEngine = Objects.requireNonNull(baseCostEngine, "baseCostEngine");
        ResolvedTemporalContext nonNullTemporalContext = Objects.requireNonNull(temporalContext, "temporalContext");
        Objects.requireNonNull(topologyVersion, "topologyVersion");
        Objects.requireNonNull(quarantineSnapshot, "quarantineSnapshot");
        Clock nonNullClock = Objects.requireNonNull(clock, "clock");

        Instant now = nonNullClock.instant();
        ResolvedRecencyCalibration resolvedCalibration =
                resolveCalibrationTicks(nonNullBaseCostEngine.engineTimeUnit());
        double horizonWeight = horizonWeight(request.getHorizonTicks(), resolvedCalibration.horizonHalfLifeTicks());
        ScenarioBundle.ScenarioBundleBuilder bundle = ScenarioBundle.builder()
                .scenarioBundleId(UUID.randomUUID().toString())
                .generatedAt(now)
                .validUntil(now.plus(request.getResultTtl()))
                .horizonTicks(request.getHorizonTicks())
                .topologyVersion(topologyVersion)
                .quarantineSnapshotId(quarantineSnapshot.snapshotId());
        RecurringSignalSummary recurringSignal = recurringSignalSummary(
                request,
                nonNullBaseCostEngine,
                nonNullTemporalContext,
                horizonWeight,
                resolvedCalibration
        );

        if (!quarantineSnapshot.hasActiveFailures()) {
            bundle.scenario(ScenarioDefinition.builder()
                    .scenarioId("baseline")
                    .label("baseline")
                    .probability(recurringSignal.hasScenario() ? 1.0d - recurringSignal.scenarioProbability() : 1.0d)
                    .explanationTags(recurringSignal.baselineTags())
                    .build());
            if (recurringSignal.hasScenario()) {
                bundle.scenario(ScenarioDefinition.builder()
                        .scenarioId(recurringSignal.scenarioId())
                        .label(recurringSignal.scenarioLabel())
                        .probability(recurringSignal.scenarioProbability())
                        .probabilityAudit(recurringSignal.probabilityAudit())
                        .explanationTags(recurringSignal.scenarioTags())
                        .liveUpdates(recurringSignal.scenarioUpdates())
                        .build());
            }
            return bundle.build();
        }

        List<String> explanationTags = mergeTags(quarantineSnapshot.explanationTags(), recurringSignal.baselineTags());
        long departureTicks = request.getDepartureTicks();
        long clearAtTicks = saturatingAdd(departureTicks, Math.max(1L, request.getHorizonTicks() / 2L));
        QuarantineProbabilitySplit probabilitySplit = quarantineProbabilitySplit(
                request,
                quarantineSnapshot,
                horizonWeight,
                resolvedCalibration
        );

        bundle.scenario(ScenarioDefinition.builder()
                .scenarioId("incident_persists")
                .label("incident_persists")
                .probability(probabilitySplit.incidentPersistsProbability())
                .probabilityAudit(probabilitySplit.incidentPersistsAudit())
                .explanationTags(mergeTags(explanationTags, recurringSignal.scenarioTags()))
                .liveUpdates(mergeLiveUpdates(
                        quarantineSnapshot.toLiveUpdates(),
                        recurringSignal.scenarioUpdates()
                ))
                .build());

        ArrayList<String> clearingTags = new ArrayList<>(mergeTags(explanationTags, recurringSignal.scenarioTags()));
        appendTag(clearingTags, TAG_RECOVERY_EXPECTED);
        bundle.scenario(ScenarioDefinition.builder()
                .scenarioId("clearing_fast")
                .label("clearing_fast")
                .probability(probabilitySplit.clearingFastProbability())
                .probabilityAudit(probabilitySplit.clearingFastAudit())
                .explanationTags(clearingTags)
                .liveUpdates(mergeLiveUpdates(
                        quarantineSnapshot.toLiveUpdates(nonNullBaseCostEngine.edgeGraph(), clearAtTicks),
                        recurringSignal.scenarioUpdates()
                ))
                .build());

        return bundle.build();
    }

    private RecurringSignalSummary recurringSignalSummary(
            ScenarioBundleRequest request,
            CostEngine baseCostEngine,
            ResolvedTemporalContext temporalContext,
            double horizonWeight,
            ResolvedRecencyCalibration resolvedCalibration
    ) {
        TemporalContextResolver resolver = Objects.requireNonNull(temporalContext.getResolver(), "temporalContext.resolver");
        if (!resolver.dayMaskAware()) {
            return RecurringSignalSummary.empty();
        }

        ProfileStore profileStore = baseCostEngine.profileStore();
        Map<Integer, ProfileSignal> signalByProfileId = new HashMap<>();
        Map<Integer, ProfileCalibration> calibrationByProfileId = new HashMap<>();
        Map<Integer, Long> observedAtTicksByProfileId = new HashMap<>();
        ArrayList<RecurringEdgeCandidate> candidates = new ArrayList<>();
        boolean weakSignalSeen = false;

        for (int edgeId = 0; edgeId < baseCostEngine.edgeGraph().edgeCount(); edgeId++) {
            int profileId = baseCostEngine.edgeGraph().getProfileId(edgeId);
            if (!profileStore.hasProfile(profileId)) {
                continue;
            }
            ProfileSignal signal = signalByProfileId.computeIfAbsent(
                    profileId,
                    ignored -> evaluateProfileSignal(profileId, request, baseCostEngine, temporalContext)
            );
            ProfileCalibration calibration = calibrationByProfileId.computeIfAbsent(
                    profileId,
                    ignored -> resolveCalibration(profileId, baseCostEngine)
            );
            long observedAtTicks = observedAtTicksByProfileId.computeIfAbsent(
                    profileId,
                    ignored -> resolveObservedAtTicks(profileId, baseCostEngine)
            );
            weakSignalSeen |= signal.weakSignal();
            if (!signal.hasScenario()) {
                continue;
            }

            double severity = baseCostEngine.edgeGraph().getBaseWeight(edgeId) * (signal.peakRatio() - 1.0d);
            candidates.add(new RecurringEdgeCandidate(
                    edgeId,
                    signal.speedFactor(),
                    signal.validFromTicks(),
                    signal.validUntilTicks(),
                    severity,
                    calibration,
                    signal.peakRatio(),
                    observedAtTicks
            ));
        }

        if (candidates.isEmpty()) {
            if (!weakSignalSeen) {
                return RecurringSignalSummary.empty();
            }
            return new RecurringSignalSummary(
                    List.of(TAG_WEAK_PERIODIC_SIGNAL, TAG_RECURRENT_SIGNAL_REJECTED),
                    null,
                    null,
                    0.0d,
                    null,
                    List.of(),
                    List.of(),
                    true
            );
        }

        candidates.sort(Comparator
                .comparingDouble(RecurringEdgeCandidate::severity).reversed()
                .thenComparingInt(RecurringEdgeCandidate::edgeId));

        double totalSeverity = 0.0d;
        double routineSeverity = 0.0d;
        double incidentSeverity = 0.0d;
        double weightedConfidence = 0.0d;
        double weightedFreshness = 0.0d;
        double maxPeakRatio = PERIODIC_PEAK_MIN_RATIO;
        int totalObservations = 0;
        boolean explicitCalibration = false;
        Long mostRecentObservedAtTicks = null;
        for (RecurringEdgeCandidate candidate : candidates) {
            totalSeverity += candidate.severity();
            weightedConfidence += candidate.calibration().confidence() * candidate.severity();
            weightedFreshness += freshnessWeight(
                    request.getDepartureTicks(),
                    candidate.observedAtTicks(),
                    resolvedCalibration.freshnessHalfLifeTicks()
            ) * candidate.severity();
            totalObservations += candidate.calibration().observationCount();
            maxPeakRatio = Math.max(maxPeakRatio, candidate.peakRatio());
            explicitCalibration |= candidate.calibration().calibrationSource()
                    == ProfileRecurrenceCalibrationStore.CalibrationSource.EXPLICIT_SOURCE;
            if (candidate.observedAtTicks() != Long.MIN_VALUE) {
                mostRecentObservedAtTicks = mostRecentObservedAtTicks == null
                        ? candidate.observedAtTicks()
                        : Math.max(mostRecentObservedAtTicks, candidate.observedAtTicks());
            }
            if (candidate.calibration().signalFlavor() == ProfileRecurrenceCalibrationStore.SignalFlavor.RECURRING_INCIDENT) {
                incidentSeverity += candidate.severity();
            } else {
                routineSeverity += candidate.severity();
            }
        }
        double summaryConfidence = totalSeverity > 0.0d ? weightedConfidence / totalSeverity : 0.0d;
        double freshnessWeight = totalSeverity > 0.0d
                ? weightedFreshness / totalSeverity
                : recencyCalibrationConfig.freshnessFloor();
        double baseScenarioProbability = calibratedScenarioProbability(summaryConfidence, totalObservations, maxPeakRatio);
        double scenarioProbability = clamp(
                baseScenarioProbability * recurringRecencyScale(freshnessWeight, horizonWeight),
                MIN_RECURRENT_SCENARIO_PROBABILITY,
                MAX_RECURRENT_SCENARIO_PROBABILITY
        );
        int updateBudget = updateBudget(summaryConfidence, totalObservations);

        ArrayList<LiveUpdate> updates = new ArrayList<>(Math.min(updateBudget, candidates.size()));
        for (int i = 0; i < candidates.size() && i < updateBudget; i++) {
            RecurringEdgeCandidate candidate = candidates.get(i);
            updates.add(LiveUpdate.of(
                    candidate.edgeId(),
                    candidate.speedFactor(),
                    candidate.validFromTicks(),
                    candidate.validUntilTicks()
            ));
        }

        boolean recurringIncident = incidentSeverity > routineSeverity;
        String scenarioId = recurringIncident ? TAG_RECURRING_INCIDENT_PEAK : TAG_PERIODIC_PEAK;
        String scenarioPresenceTag = recurringIncident ? TAG_RECURRING_INCIDENT_RISK : TAG_PERIODIC_SIGNAL_PRESENT;
        String confidenceTag = confidenceTag(summaryConfidence);
        String calibrationTag = explicitCalibration ? TAG_RECURRENT_CALIBRATION_EXPLICIT : TAG_RECURRENT_CALIBRATION_DERIVED;

        ArrayList<String> baselineTags = new ArrayList<>();
        appendTag(baselineTags, scenarioPresenceTag);
        appendTag(baselineTags, confidenceTag);
        appendTag(baselineTags, calibrationTag);

        ArrayList<String> scenarioTags = new ArrayList<>();
        appendTag(scenarioTags, scenarioId);
        appendTag(scenarioTags, confidenceTag);
        appendTag(scenarioTags, calibrationTag);

        return new RecurringSignalSummary(
                List.copyOf(baselineTags),
                scenarioId,
                scenarioId,
                scenarioProbability,
                buildProbabilityAudit(
                        EVIDENCE_SOURCE_PROFILE_RECENCY,
                        mostRecentObservedAtTicks,
                        request.getDepartureTicks(),
                        freshnessWeight,
                        horizonWeight,
                        baseScenarioProbability,
                        scenarioProbability
                ),
                List.copyOf(scenarioTags),
                List.copyOf(updates),
                weakSignalSeen
        );
    }

    private ProfileSignal evaluateProfileSignal(
            int profileId,
            ScenarioBundleRequest request,
            CostEngine baseCostEngine,
            ResolvedTemporalContext temporalContext
    ) {
        ProfileStore.TemporalPatternMetadata metadata = baseCostEngine.profileStore().getTemporalPatternMetadata(profileId);
        ProfileStore.TemporalPatternClass patternClass = metadata.temporalPatternClass();
        if (patternClass == ProfileStore.TemporalPatternClass.WEAK_SIGNAL_PERIODIC) {
            return ProfileSignal.weakOnly();
        }
        if (patternClass != ProfileStore.TemporalPatternClass.STRICT_PERIODIC
                && patternClass != ProfileStore.TemporalPatternClass.MIXED_PERSISTENT_AND_PERIODIC) {
            return ProfileSignal.none();
        }

        float currentMultiplier = sampleProfileMultiplier(
                profileId,
                request.getDepartureTicks(),
                baseCostEngine,
                temporalContext
        );
        PeakWindow peakWindow = strongestPeakWindow(profileId, request, baseCostEngine, temporalContext, currentMultiplier);
        double peakRatio = peakWindow.peakMultiplier() / currentMultiplier;
        if (!(peakRatio >= PERIODIC_PEAK_MIN_RATIO)) {
            return ProfileSignal.none();
        }

        float speedFactor = (float) Math.max(0.0d, Math.min(1.0d, currentMultiplier / peakWindow.peakMultiplier()));
        if (!(speedFactor < 1.0f)) {
            return ProfileSignal.none();
        }
        return new ProfileSignal(
                speedFactor,
                peakRatio,
                peakWindow.validFromTicks(),
                peakWindow.validUntilTicks(),
                false
        );
    }

    private PeakWindow strongestPeakWindow(
            int profileId,
            ScenarioBundleRequest request,
            CostEngine baseCostEngine,
            ResolvedTemporalContext temporalContext,
            float currentMultiplier
    ) {
        long bucketStepTicks = Math.max(
                1L,
                (long) baseCostEngine.bucketSizeSeconds() * baseCostEngine.engineTimeUnit().ticksPerSecond()
        );
        long departureTicks = request.getDepartureTicks();
        long horizonTicks = Math.max(0L, request.getHorizonTicks());
        long finalSampleTicks = saturatingAdd(departureTicks, horizonTicks);

        float peakMultiplier = currentMultiplier;
        long bestSampleTicks = departureTicks;
        for (long offset = bucketStepTicks; offset <= horizonTicks; offset = saturatingAdd(offset, bucketStepTicks)) {
            long sampleTicks = saturatingAdd(departureTicks, offset);
            if (sampleTicks > finalSampleTicks) {
                sampleTicks = finalSampleTicks;
            }
            float sampleMultiplier = sampleProfileMultiplier(profileId, sampleTicks, baseCostEngine, temporalContext);
            if (sampleMultiplier > peakMultiplier) {
                peakMultiplier = sampleMultiplier;
                bestSampleTicks = sampleTicks;
            }
            if (sampleTicks == finalSampleTicks) {
                break;
            }
        }

        long validFromTicks = bestSampleTicks;
        long validUntilTicks = Math.min(
                saturatingAdd(bestSampleTicks, bucketStepTicks),
                saturatingAdd(finalSampleTicks, 1L)
        );
        if (validUntilTicks <= validFromTicks) {
            validUntilTicks = saturatingAdd(validFromTicks, 1L);
        }
        return new PeakWindow(peakMultiplier, validFromTicks, validUntilTicks);
    }

    private float sampleProfileMultiplier(
            int profileId,
            long sampleTicks,
            CostEngine baseCostEngine,
            ResolvedTemporalContext temporalContext
    ) {
        ProfileStore profileStore = baseCostEngine.profileStore();
        TemporalContextResolver resolver = Objects.requireNonNull(temporalContext.getResolver(), "temporalContext.resolver");
        int dayOfWeek = resolver.resolveDayOfWeek(sampleTicks, baseCostEngine.engineTimeUnit());
        return switch (baseCostEngine.temporalSamplingPolicy()) {
            case DISCRETE -> {
                int bucketIndex = resolver.resolveBucketIndex(
                        sampleTicks,
                        baseCostEngine.bucketSizeSeconds(),
                        baseCostEngine.engineTimeUnit()
                );
                yield temporalContext.isDayMaskAware()
                        ? profileStore.getMultiplierForDay(profileId, dayOfWeek, bucketIndex)
                        : profileStore.getMultiplier(profileId, bucketIndex);
            }
            case INTERPOLATED -> {
                long bucketSizeTicks = (long) baseCostEngine.bucketSizeSeconds() * baseCostEngine.engineTimeUnit().ticksPerSecond();
                double fractionalBucket = resolver.resolveFractionalBucket(
                        sampleTicks,
                        bucketSizeTicks,
                        baseCostEngine.engineTimeUnit()
                );
                yield temporalContext.isDayMaskAware()
                        ? profileStore.interpolateForDay(profileId, dayOfWeek, fractionalBucket)
                        : profileStore.interpolate(profileId, fractionalBucket);
            }
        };
    }

    private ProfileCalibration resolveCalibration(int profileId, CostEngine baseCostEngine) {
        ProfileRecurrenceCalibrationStore recurrenceCalibrationStore = baseCostEngine.recurrenceCalibrationStore();
        if (recurrenceCalibrationStore != null && recurrenceCalibrationStore.hasCalibration(profileId)) {
            ProfileRecurrenceCalibrationStore.ProfileRecurrenceCalibration calibration =
                    recurrenceCalibrationStore.calibration(profileId);
            return new ProfileCalibration(
                    calibration.confidence(),
                    calibration.observationCount(),
                    calibration.signalFlavor(),
                    calibration.calibrationSource(),
                    calibration.confidence() >= 0.80f
                            ? ProfileStore.RecurringCalibrationPosture.HIGH_CONFIDENCE
                            : calibration.confidence() >= 0.55f
                            ? ProfileStore.RecurringCalibrationPosture.MEDIUM_CONFIDENCE
                            : ProfileStore.RecurringCalibrationPosture.LOW_CONFIDENCE
            );
        }

        ProfileStore.TemporalPatternMetadata metadata = baseCostEngine.profileStore().getTemporalPatternMetadata(profileId);
        return new ProfileCalibration(
                metadata.recurringConfidence(),
                metadata.recurringSupportWindowCount(),
                metadata.recurringSignalFlavor(),
                ProfileRecurrenceCalibrationStore.CalibrationSource.DERIVED_PROFILE_SHAPE,
                metadata.recurringCalibrationPosture()
        );
    }

    private long resolveObservedAtTicks(int profileId, CostEngine baseCostEngine) {
        ProfileRecencyCalibrationStore recencyCalibrationStore = baseCostEngine.recencyCalibrationStore();
        if (recencyCalibrationStore != null && recencyCalibrationStore.hasCalibration(profileId)) {
            return recencyCalibrationStore.calibration(profileId).lastObservedAtTicks();
        }
        return Long.MIN_VALUE;
    }

    private QuarantineProbabilitySplit quarantineProbabilitySplit(
            ScenarioBundleRequest request,
            FailureQuarantine.Snapshot quarantineSnapshot,
            double horizonWeight,
            ResolvedRecencyCalibration resolvedCalibration
    ) {
        long freshestObservedAtTicks = quarantineSnapshot.mostRecentObservedAtTicks();
        Long observedAtTicks = freshestObservedAtTicks == Long.MIN_VALUE ? null : freshestObservedAtTicks;
        double freshnessWeight = freshnessWeight(
                request.getDepartureTicks(),
                freshestObservedAtTicks,
                resolvedCalibration.freshnessHalfLifeTicks()
        );
        double incidentPersistsProbability = clamp(
                recencyCalibrationConfig.incidentPersistsBaseProbability()
                        + (recencyCalibrationConfig.incidentPersistsRange() * freshnessWeight * horizonWeight),
                recencyCalibrationConfig.minIncidentPersistsProbability(),
                recencyCalibrationConfig.maxIncidentPersistsProbability()
        );
        double clearingFastProbability = 1.0d - incidentPersistsProbability;
        return new QuarantineProbabilitySplit(
                incidentPersistsProbability,
                clearingFastProbability,
                buildProbabilityAudit(
                        EVIDENCE_SOURCE_QUARANTINE,
                        observedAtTicks,
                        request.getDepartureTicks(),
                        freshnessWeight,
                        horizonWeight,
                        INCIDENT_PERSISTS_PROBABILITY,
                        incidentPersistsProbability
                ),
                buildProbabilityAudit(
                        EVIDENCE_SOURCE_QUARANTINE,
                        observedAtTicks,
                        request.getDepartureTicks(),
                        freshnessWeight,
                        horizonWeight,
                        CLEARING_FAST_PROBABILITY,
                        clearingFastProbability
                )
        );
    }

    private static double calibratedScenarioProbability(
            double recurringConfidence,
            int observationCount,
            double peakRatio
    ) {
        double severityScore = clamp((peakRatio - PERIODIC_PEAK_MIN_RATIO) / 1.50d, 0.0d, 1.0d);
        double observationScore = clamp(Math.log1p(Math.max(0, observationCount)) / Math.log(17.0d), 0.0d, 1.0d);
        return clamp(
                MIN_RECURRENT_SCENARIO_PROBABILITY
                        + (0.30d * recurringConfidence)
                        + (0.20d * severityScore)
                        + (0.10d * observationScore),
                MIN_RECURRENT_SCENARIO_PROBABILITY,
                MAX_RECURRENT_SCENARIO_PROBABILITY
        );
    }

    private static int updateBudget(double recurringConfidence, int observationCount) {
        int dynamicBudget = (int) Math.ceil(4.0d + (12.0d * recurringConfidence) + Math.sqrt(Math.max(0, observationCount)));
        return Math.max(4, Math.min(MAX_RECURRENT_UPDATES, dynamicBudget));
    }

    private static String confidenceTag(double recurringConfidence) {
        if (recurringConfidence >= 0.80d) {
            return TAG_RECURRENT_CONFIDENCE_HIGH;
        }
        if (recurringConfidence >= 0.55d) {
            return TAG_RECURRENT_CONFIDENCE_MEDIUM;
        }
        return TAG_RECURRENT_CONFIDENCE_LOW;
    }

    private static List<String> mergeTags(List<String> left, List<String> right) {
        ArrayList<String> merged = new ArrayList<>();
        if (left != null) {
            for (String tag : left) {
                appendTag(merged, tag);
            }
        }
        if (right != null) {
            for (String tag : right) {
                appendTag(merged, tag);
            }
        }
        return List.copyOf(merged);
    }

    private static void appendTag(List<String> tags, String tag) {
        if (tag == null || tag.isBlank() || tags.contains(tag)) {
            return;
        }
        tags.add(tag);
    }

    private static List<LiveUpdate> mergeLiveUpdates(List<LiveUpdate> left, List<LiveUpdate> right) {
        LinkedHashMap<Integer, LiveUpdate> merged = new LinkedHashMap<>();
        if (left != null) {
            for (LiveUpdate update : left) {
                merged.put(update.edgeId(), update);
            }
        }
        if (right != null) {
            for (LiveUpdate update : right) {
                merged.merge(update.edgeId(), update, DefaultScenarioBundleResolver::moreSevereUpdate);
            }
        }
        return List.copyOf(merged.values());
    }

    private static LiveUpdate moreSevereUpdate(LiveUpdate left, LiveUpdate right) {
        boolean overlaps = left.validFromTicks() < right.validUntilTicks()
                && right.validFromTicks() < left.validUntilTicks();
        if (!overlaps) {
            return left.validFromTicks() <= right.validFromTicks() ? left : right;
        }
        float speedFactor = Math.min(left.speedFactor(), right.speedFactor());
        long validFromTicks = Math.min(left.validFromTicks(), right.validFromTicks());
        long validUntilTicks = Math.max(left.validUntilTicks(), right.validUntilTicks());
        return LiveUpdate.of(left.edgeId(), speedFactor, validFromTicks, validUntilTicks);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private ResolvedRecencyCalibration resolveCalibrationTicks(TimeUtils.EngineTimeUnit engineTimeUnit) {
        return new ResolvedRecencyCalibration(
                durationToTicks(recencyCalibrationConfig.freshnessHalfLife(), engineTimeUnit),
                durationToTicks(recencyCalibrationConfig.horizonHalfLife(), engineTimeUnit)
        );
    }

    private double freshnessWeight(long departureTicks, long observedAtTicks, long freshnessHalfLifeTicks) {
        if (observedAtTicks == Long.MIN_VALUE) {
            return recencyCalibrationConfig.freshnessFloor();
        }
        long ageTicks = Math.max(0L, departureTicks - observedAtTicks);
        double decay = Math.pow(0.5d, (double) ageTicks / (double) freshnessHalfLifeTicks);
        return recencyCalibrationConfig.freshnessFloor()
                + ((1.0d - recencyCalibrationConfig.freshnessFloor()) * decay);
    }

    private double horizonWeight(long horizonTicks, long horizonHalfLifeTicks) {
        long nonNegativeHorizonTicks = Math.max(0L, horizonTicks);
        double decay = Math.pow(0.5d, (double) nonNegativeHorizonTicks / (double) horizonHalfLifeTicks);
        return recencyCalibrationConfig.horizonFloor()
                + ((1.0d - recencyCalibrationConfig.horizonFloor()) * decay);
    }

    private double recurringRecencyScale(double freshnessWeight, double horizonWeight) {
        return recencyCalibrationConfig.recurringScaleFloor()
                + (recencyCalibrationConfig.recurringScaleRange() * freshnessWeight * horizonWeight);
    }

    private long durationToTicks(Duration duration, TimeUtils.EngineTimeUnit engineTimeUnit) {
        long converted = TimeUtils.normalizeToEngineTicks(
                duration.toMillis(),
                TimeUtils.EngineTimeUnit.MILLISECONDS,
                engineTimeUnit
        );
        return Math.max(1L, converted);
    }

    private ScenarioProbabilityAudit buildProbabilityAudit(
            String evidenceSource,
            Long observedAtTicks,
            long departureTicks,
            double freshnessWeight,
            double horizonWeight,
            double baseProbability,
            double adjustedProbability
    ) {
        Long evidenceAgeTicks = observedAtTicks == null ? null : Math.max(0L, departureTicks - observedAtTicks);
        return ScenarioProbabilityAudit.builder()
                .policyId(recencyCalibrationConfig.policyId())
                .evidenceSource(evidenceSource)
                .observedAtTicks(observedAtTicks)
                .evidenceAgeTicks(evidenceAgeTicks)
                .freshnessWeight(freshnessWeight)
                .horizonWeight(horizonWeight)
                .baseProbability(baseProbability)
                .adjustedProbability(adjustedProbability)
                .build();
    }

    private static long saturatingAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ex) {
            return right >= 0L ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    private record PeakWindow(float peakMultiplier, long validFromTicks, long validUntilTicks) {
    }

    private record RecurringEdgeCandidate(
            int edgeId,
            float speedFactor,
            long validFromTicks,
            long validUntilTicks,
            double severity,
            ProfileCalibration calibration,
            double peakRatio,
            long observedAtTicks
    ) {
    }

    private record ProfileSignal(
            float speedFactor,
            double peakRatio,
            long validFromTicks,
            long validUntilTicks,
            boolean weakSignal
    ) {
        static ProfileSignal none() {
            return new ProfileSignal(1.0f, 1.0d, Long.MIN_VALUE, Long.MIN_VALUE + 1L, false);
        }

        static ProfileSignal weakOnly() {
            return new ProfileSignal(1.0f, 1.0d, Long.MIN_VALUE, Long.MIN_VALUE + 1L, true);
        }

        boolean hasScenario() {
            return speedFactor < 1.0f;
        }
    }

    private record ProfileCalibration(
            float confidence,
            int observationCount,
            ProfileRecurrenceCalibrationStore.SignalFlavor signalFlavor,
            ProfileRecurrenceCalibrationStore.CalibrationSource calibrationSource,
            ProfileStore.RecurringCalibrationPosture calibrationPosture
    ) {
    }

    private record RecurringSignalSummary(
            List<String> baselineTags,
            String scenarioId,
            String scenarioLabel,
            double scenarioProbability,
            ScenarioProbabilityAudit probabilityAudit,
            List<String> scenarioTags,
            List<LiveUpdate> scenarioUpdates,
            boolean weakSignalSeen
    ) {
        static RecurringSignalSummary empty() {
            return new RecurringSignalSummary(List.of(), null, null, 0.0d, null, List.of(), List.of(), false);
        }

        boolean hasScenario() {
            return !scenarioUpdates.isEmpty();
        }
    }

    private record QuarantineProbabilitySplit(
            double incidentPersistsProbability,
            double clearingFastProbability,
            ScenarioProbabilityAudit incidentPersistsAudit,
            ScenarioProbabilityAudit clearingFastAudit
    ) {
    }

    private record ResolvedRecencyCalibration(long freshnessHalfLifeTicks, long horizonHalfLifeTicks) {
    }
}
