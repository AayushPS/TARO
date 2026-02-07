package org.Aayush.serialization.flatbuffers;

import org.Aayush.core.time.TimeUtils;
import org.Aayush.serialization.flatbuffers.taro.model.Metadata;
import org.Aayush.serialization.flatbuffers.taro.model.Model;
import org.Aayush.serialization.flatbuffers.taro.model.TimeUnit;

/**
 * Shared runtime validator for model-level metadata contracts.
 */
public final class ModelContractValidator {

    private static final long EXPECTED_SCHEMA_VERSION = 1L;

    private ModelContractValidator() {
        throw new AssertionError("Utility class - do not instantiate");
    }

    /**
     * Validates metadata contract required for deterministic runtime behavior.
     *
     * @param model parsed FlatBuffers model root.
     * @param loaderName logical loader name for error messages.
     * @return canonical engine time unit from metadata.
     */
    public static TimeUtils.EngineTimeUnit validateMetadataContract(Model model, String loaderName) {
        if (model == null) {
            throw new IllegalArgumentException(loaderName + ": model root cannot be null");
        }

        Metadata metadata = model.metadata();
        if (metadata == null) {
            throw new IllegalArgumentException(loaderName + ": metadata missing");
        }

        long schemaVersion = metadata.schemaVersion();
        if (schemaVersion != EXPECTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    loaderName + ": unsupported schema_version " + schemaVersion
                            + " (expected " + EXPECTED_SCHEMA_VERSION + ")"
            );
        }

        TimeUtils.EngineTimeUnit unit = fromFlatBufferTimeUnit(metadata.timeUnit(), loaderName);
        TimeUtils.validateTickDurationNs(unit, metadata.tickDurationNs());
        return unit;
    }

    private static TimeUtils.EngineTimeUnit fromFlatBufferTimeUnit(int fbTimeUnit, String loaderName) {
        if (fbTimeUnit == TimeUnit.SECONDS) {
            return TimeUtils.EngineTimeUnit.SECONDS;
        }
        if (fbTimeUnit == TimeUnit.MILLISECONDS) {
            return TimeUtils.EngineTimeUnit.MILLISECONDS;
        }
        throw new IllegalArgumentException(loaderName + ": unsupported time_unit value " + fbTimeUnit);
    }
}
