package org.Aayush.routing.future;

import org.Aayush.routing.overlay.LiveUpdate;
import org.Aayush.routing.topology.FailureQuarantine;
import org.Aayush.routing.topology.TopologyVersion;
import org.Aayush.routing.traits.temporal.ResolvedTemporalContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Stable bundle identity helper so all scenario materializers share one canonical hash policy.
 */
final class ScenarioBundleIdentityHasher {
    private static final String SCENARIO_BUNDLE_ID_PREFIX = "bundle-";
    private static final String NULL_TOKEN = "<null>";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private ScenarioBundleIdentityHasher() {
    }

    static String stableBundleId(
            ScenarioBundleRequest request,
            ResolvedTemporalContext temporalContext,
            TopologyVersion topologyVersion,
            FailureQuarantine.Snapshot quarantineSnapshot,
            List<ScenarioDefinition> scenarios
    ) {
        StringBuilder canonical = new StringBuilder(1_024);
        appendCanonical(canonical, "departureTicks", request.getDepartureTicks());
        appendCanonical(canonical, "horizonTicks", request.getHorizonTicks());
        appendCanonical(canonical, "temporalTraitId", temporalContext.getTemporalTraitId());
        appendCanonical(canonical, "temporalStrategyId", temporalContext.getTemporalStrategyId());
        appendCanonical(canonical, "timezonePolicyId", temporalContext.getTimezonePolicyId());
        appendCanonical(canonical, "zoneId", temporalContext.getZoneId());
        appendCanonical(canonical, "dayMaskAware", temporalContext.isDayMaskAware());
        appendCanonical(canonical, "maxDiscretizationDriftSeconds", temporalContext.getMaxDiscretizationDriftSeconds());
        appendCanonical(canonical, "granularityLossPolicy", temporalContext.getGranularityLossPolicy());
        appendCanonical(canonical, "modelVersion", topologyVersion.getModelVersion());
        appendCanonical(canonical, "topologyVersion", topologyVersion.getTopologyVersion());
        appendCanonical(canonical, "topologyGeneratedAt", topologyVersion.getGeneratedAt());
        appendCanonical(canonical, "sourceDataLineageHash", topologyVersion.getSourceDataLineageHash());
        appendCanonical(canonical, "changeSetHash", topologyVersion.getChangeSetHash());
        appendCanonical(canonical, "quarantineSnapshotId", quarantineSnapshot.snapshotId());
        appendCanonical(canonical, "scenarioCount", scenarios.size());

        for (int i = 0; i < scenarios.size(); i++) {
            ScenarioDefinition scenario = scenarios.get(i);
            appendCanonical(canonical, "scenario[" + i + "].scenarioId", scenario.getScenarioId());
            appendCanonical(canonical, "scenario[" + i + "].label", scenario.getLabel());
            appendCanonical(canonical, "scenario[" + i + "].probabilityBits", Double.doubleToLongBits(scenario.getProbability()));
            appendCanonicalList(canonical, "scenario[" + i + "].explanationTags", scenario.getExplanationTags());
            appendCanonicalLiveUpdates(canonical, "scenario[" + i + "].liveUpdates", scenario.getLiveUpdates());
            appendCanonicalAudit(canonical, "scenario[" + i + "].probabilityAudit", scenario.getProbabilityAudit());
        }

        return SCENARIO_BUNDLE_ID_PREFIX + sha256Hex(canonical.toString());
    }

    private static void appendCanonicalAudit(StringBuilder canonical, String prefix, ScenarioProbabilityAudit audit) {
        appendCanonical(canonical, prefix + ".policyId", audit == null ? null : audit.getPolicyId());
        appendCanonical(canonical, prefix + ".evidenceSource", audit == null ? null : audit.getEvidenceSource());
        appendCanonical(canonical, prefix + ".observedAtTicks", audit == null ? null : audit.getObservedAtTicks());
        appendCanonical(canonical, prefix + ".evidenceAgeTicks", audit == null ? null : audit.getEvidenceAgeTicks());
        appendCanonical(canonical, prefix + ".freshnessWeightBits", audit == null ? null : Double.doubleToLongBits(audit.getFreshnessWeight()));
        appendCanonical(canonical, prefix + ".horizonWeightBits", audit == null ? null : Double.doubleToLongBits(audit.getHorizonWeight()));
        appendCanonical(canonical, prefix + ".baseProbabilityBits", audit == null ? null : Double.doubleToLongBits(audit.getBaseProbability()));
        appendCanonical(canonical, prefix + ".adjustedProbabilityBits", audit == null ? null : Double.doubleToLongBits(audit.getAdjustedProbability()));
        appendCanonicalStructuralAudit(canonical, prefix + ".structuralPriorAudit", audit == null ? null : audit.getStructuralPriorAudit());
    }

    private static void appendCanonicalStructuralAudit(
            StringBuilder canonical,
            String prefix,
            ScenarioStructuralPriorAudit audit
    ) {
        appendCanonical(canonical, prefix + ".policyId", audit == null ? null : audit.getPolicyId());
        appendCanonical(canonical, prefix + ".normalizedDegreeScoreBits", audit == null ? null : Double.doubleToLongBits(audit.getNormalizedDegreeScore()));
        appendCanonical(canonical, prefix + ".centeredDegreeSignalBits", audit == null ? null : Double.doubleToLongBits(audit.getCenteredDegreeSignal()));
        appendCanonical(canonical, prefix + ".appliedAdjustmentBits", audit == null ? null : Double.doubleToLongBits(audit.getAppliedAdjustment()));
        appendCanonical(canonical, prefix + ".homophilyScoreBits", audit == null ? null : Double.doubleToLongBits(audit.getHomophilyScore()));
        appendCanonical(canonical, prefix + ".affectedEdgeCount", audit == null ? null : audit.getAffectedEdgeCount());
    }

    private static void appendCanonicalLiveUpdates(StringBuilder canonical, String prefix, List<LiveUpdate> liveUpdates) {
        int size = liveUpdates == null ? 0 : liveUpdates.size();
        appendCanonical(canonical, prefix + ".count", size);
        if (liveUpdates == null) {
            return;
        }
        for (int i = 0; i < liveUpdates.size(); i++) {
            LiveUpdate update = liveUpdates.get(i);
            appendCanonical(canonical, prefix + "[" + i + "].edgeId", update.edgeId());
            appendCanonical(canonical, prefix + "[" + i + "].speedFactorBits", Float.floatToIntBits(update.speedFactor()));
            appendCanonical(canonical, prefix + "[" + i + "].validFromTicks", update.validFromTicks());
            appendCanonical(canonical, prefix + "[" + i + "].validUntilTicks", update.validUntilTicks());
        }
    }

    private static void appendCanonicalList(StringBuilder canonical, String prefix, List<String> values) {
        int size = values == null ? 0 : values.size();
        appendCanonical(canonical, prefix + ".count", size);
        if (values == null) {
            return;
        }
        for (int i = 0; i < values.size(); i++) {
            appendCanonical(canonical, prefix + "[" + i + "]", values.get(i));
        }
    }

    private static void appendCanonical(StringBuilder canonical, String key, Object value) {
        canonical.append(key)
                .append('=')
                .append(value == null ? NULL_TOKEN : value)
                .append('\n');
    }

    private static String sha256Hex(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("failed to initialize SHA-256 for scenario bundle identity", ex);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            chars[i * 2] = HEX[value >>> 4];
            chars[(i * 2) + 1] = HEX[value & 0x0F];
        }
        return new String(chars);
    }
}
