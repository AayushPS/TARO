package org.Aayush.serialization.flatbuffers;

import com.google.flatbuffers.FlatBufferBuilder;
import org.Aayush.core.time.TimeUtils;
import org.Aayush.serialization.flatbuffers.taro.model.Metadata;
import org.Aayush.serialization.flatbuffers.taro.model.Model;
import org.Aayush.serialization.flatbuffers.taro.model.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Model Contract Validator Tests")
class ModelContractValidatorTest {

    @Test
    @DisplayName("Valid metadata returns SECONDS engine unit")
    void testValidSecondsMetadata() {
        Model model = parseModel(buildModelBuffer(true, 1L, TimeUnit.SECONDS, 1_000_000_000L));
        TimeUtils.EngineTimeUnit unit = ModelContractValidator.validateMetadataContract(model, "ValidatorTest");
        assertEquals(TimeUtils.EngineTimeUnit.SECONDS, unit);
    }

    @Test
    @DisplayName("Valid metadata returns MILLISECONDS engine unit")
    void testValidMillisecondsMetadata() {
        Model model = parseModel(buildModelBuffer(true, 1L, TimeUnit.MILLISECONDS, 1_000_000L));
        TimeUtils.EngineTimeUnit unit = ModelContractValidator.validateMetadataContract(model, "ValidatorTest");
        assertEquals(TimeUtils.EngineTimeUnit.MILLISECONDS, unit);
    }

    @Test
    @DisplayName("Null model is rejected")
    void testNullModelRejected() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ModelContractValidator.validateMetadataContract(null, "ValidatorTest")
        );
        assertTrue(ex.getMessage().contains("model root"));
    }

    @Test
    @DisplayName("Missing metadata is rejected")
    void testMissingMetadataRejected() {
        Model model = parseModel(buildModelBuffer(false, 1L, TimeUnit.SECONDS, 1_000_000_000L));
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ModelContractValidator.validateMetadataContract(model, "ValidatorTest")
        );
        assertTrue(ex.getMessage().contains("metadata"));
    }

    @Test
    @DisplayName("Unsupported schema version is rejected")
    void testUnsupportedSchemaVersionRejected() {
        Model model = parseModel(buildModelBuffer(true, 2L, TimeUnit.SECONDS, 1_000_000_000L));
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ModelContractValidator.validateMetadataContract(model, "ValidatorTest")
        );
        assertTrue(ex.getMessage().contains("schema_version"));
    }

    @Test
    @DisplayName("Unsupported time unit value is rejected")
    void testUnsupportedTimeUnitRejected() {
        Model model = parseModel(buildModelBuffer(true, 1L, 42, 1_000_000_000L));
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ModelContractValidator.validateMetadataContract(model, "ValidatorTest")
        );
        assertTrue(ex.getMessage().contains("time_unit"));
    }

    @Test
    @DisplayName("Tick duration mismatch is rejected")
    void testTickDurationMismatchRejected() {
        Model model = parseModel(buildModelBuffer(true, 1L, TimeUnit.MILLISECONDS, 1_000_000_000L));
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ModelContractValidator.validateMetadataContract(model, "ValidatorTest")
        );
        assertTrue(ex.getMessage().contains("tick_duration_ns"));
    }

    @Test
    @DisplayName("Profile timezone parsing accepts valid ids")
    void testProfileTimezoneParsing() {
        ZoneId zoneId = ModelContractValidator.parseProfileTimezone("America/New_York", "ValidatorTest");
        assertEquals("America/New_York", zoneId.getId());
    }

    @Test
    @DisplayName("Profile timezone parsing rejects missing or invalid values")
    void testProfileTimezoneParsingValidation() {
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> ModelContractValidator.parseProfileTimezone("   ", "ValidatorTest")
        );
        assertTrue(missing.getMessage().contains("profile_timezone"));

        IllegalArgumentException invalid = assertThrows(
                IllegalArgumentException.class,
                () -> ModelContractValidator.parseProfileTimezone("Invalid/Timezone", "ValidatorTest")
        );
        assertTrue(invalid.getMessage().contains("profile_timezone"));
    }

    private Model parseModel(ByteBuffer buffer) {
        return Model.getRootAsModel(buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN));
    }

    private ByteBuffer buildModelBuffer(
            boolean includeMetadata,
            long schemaVersion,
            int timeUnit,
            long tickDurationNs
    ) {
        FlatBufferBuilder builder = new FlatBufferBuilder(256);

        int metadataRef = 0;
        if (includeMetadata) {
            int modelVersion = builder.createString("validator-test");
            Metadata.startMetadata(builder);
            Metadata.addSchemaVersion(builder, schemaVersion);
            Metadata.addModelVersion(builder, modelVersion);
            Metadata.addTimeUnit(builder, timeUnit);
            Metadata.addTickDurationNs(builder, tickDurationNs);
            metadataRef = Metadata.endMetadata(builder);
        }

        Model.startModel(builder);
        if (metadataRef != 0) {
            Model.addMetadata(builder, metadataRef);
        }
        int root = Model.endModel(builder);
        Model.finishModelBuffer(builder, root);

        return ByteBuffer.wrap(builder.sizedByteArray()).order(ByteOrder.LITTLE_ENDIAN);
    }
}
