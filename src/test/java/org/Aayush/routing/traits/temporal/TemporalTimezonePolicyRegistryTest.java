package org.Aayush.routing.traits.temporal;

import org.Aayush.routing.core.RouteCore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 16 TemporalTimezonePolicyRegistry Tests")
class TemporalTimezonePolicyRegistryTest {

    @Test
    @DisplayName("Default registry exposes UTC and MODEL_TIMEZONE policies")
    void testDefaultRegistryBuiltIns() {
        TemporalTimezonePolicyRegistry registry = TemporalTimezonePolicyRegistry.defaultRegistry();
        assertNotNull(registry.policy(TemporalTimezonePolicyRegistry.POLICY_UTC));
        assertNotNull(registry.policy(TemporalTimezonePolicyRegistry.POLICY_MODEL_TIMEZONE));
        assertTrue(registry.policyIds().contains(TemporalTimezonePolicyRegistry.POLICY_UTC));
        assertTrue(registry.policyIds().contains(TemporalTimezonePolicyRegistry.POLICY_MODEL_TIMEZONE));
    }

    @Test
    @DisplayName("Null policy lookup returns null")
    void testNullLookupReturnsNull() {
        TemporalTimezonePolicyRegistry registry = TemporalTimezonePolicyRegistry.defaultRegistry();
        assertNull(registry.policy(null));
    }

    @Test
    @DisplayName("Null custom policy collection falls back to built-ins")
    void testNullCustomCollectionUsesBuiltIns() {
        TemporalTimezonePolicyRegistry registry =
                new TemporalTimezonePolicyRegistry((Collection<? extends TemporalTimezonePolicy>) null);
        assertNotNull(registry.policy(TemporalTimezonePolicyRegistry.POLICY_UTC));
        assertNotNull(registry.policy(TemporalTimezonePolicyRegistry.POLICY_MODEL_TIMEZONE));
    }

    @Test
    @DisplayName("UTC policy always resolves UTC zone")
    void testUtcPolicyResolvesUtc() {
        TemporalTimezonePolicyRegistry registry = TemporalTimezonePolicyRegistry.defaultRegistry();
        TemporalTimezonePolicy policy = registry.policy(TemporalTimezonePolicyRegistry.POLICY_UTC);
        assertEquals("Z", policy.resolveZoneId(TemporalRuntimeConfig.calendarUtc()).getId());
    }

    @Test
    @DisplayName("MODEL_TIMEZONE policy resolves valid timezone ids")
    void testModelTimezonePolicyResolvesZone() {
        TemporalTimezonePolicyRegistry registry = TemporalTimezonePolicyRegistry.defaultRegistry();
        TemporalTimezonePolicy policy = registry.policy(TemporalTimezonePolicyRegistry.POLICY_MODEL_TIMEZONE);
        ZoneId zone = policy.resolveZoneId(TemporalRuntimeConfig.calendarModelTimezone("America/New_York"));
        assertEquals("America/New_York", zone.getId());
    }

    @Test
    @DisplayName("MODEL_TIMEZONE policy rejects missing timezone with deterministic reason code")
    void testModelTimezonePolicyRequiresTimezone() {
        TemporalTimezonePolicyRegistry registry = TemporalTimezonePolicyRegistry.defaultRegistry();
        TemporalTimezonePolicy policy = registry.policy(TemporalTimezonePolicyRegistry.POLICY_MODEL_TIMEZONE);
        TemporalTimezonePolicy.PolicyResolutionException ex = assertThrows(
                TemporalTimezonePolicy.PolicyResolutionException.class,
                () -> policy.resolveZoneId(
                        TemporalRuntimeConfig.builder()
                                .temporalTraitId(TemporalTraitCatalog.TRAIT_CALENDAR)
                                .timezonePolicyId(TemporalTimezonePolicyRegistry.POLICY_MODEL_TIMEZONE)
                                .build()
                )
        );
        assertEquals(RouteCore.REASON_MODEL_TIMEZONE_REQUIRED, ex.reasonCode());
    }

    @Test
    @DisplayName("MODEL_TIMEZONE policy rejects blank timezone with deterministic reason code")
    void testModelTimezonePolicyRejectsBlankTimezone() {
        TemporalTimezonePolicyRegistry registry = TemporalTimezonePolicyRegistry.defaultRegistry();
        TemporalTimezonePolicy policy = registry.policy(TemporalTimezonePolicyRegistry.POLICY_MODEL_TIMEZONE);
        TemporalTimezonePolicy.PolicyResolutionException ex = assertThrows(
                TemporalTimezonePolicy.PolicyResolutionException.class,
                () -> policy.resolveZoneId(
                        TemporalRuntimeConfig.builder()
                                .temporalTraitId(TemporalTraitCatalog.TRAIT_CALENDAR)
                                .timezonePolicyId(TemporalTimezonePolicyRegistry.POLICY_MODEL_TIMEZONE)
                                .modelProfileTimezone("   ")
                                .build()
                )
        );
        assertEquals(RouteCore.REASON_MODEL_TIMEZONE_REQUIRED, ex.reasonCode());
    }

    @Test
    @DisplayName("MODEL_TIMEZONE policy rejects invalid timezone ids")
    void testModelTimezonePolicyRejectsInvalidTimezone() {
        TemporalTimezonePolicyRegistry registry = TemporalTimezonePolicyRegistry.defaultRegistry();
        TemporalTimezonePolicy policy = registry.policy(TemporalTimezonePolicyRegistry.POLICY_MODEL_TIMEZONE);
        TemporalTimezonePolicy.PolicyResolutionException ex = assertThrows(
                TemporalTimezonePolicy.PolicyResolutionException.class,
                () -> policy.resolveZoneId(TemporalRuntimeConfig.calendarModelTimezone("Invalid/Timezone"))
        );
        assertEquals(RouteCore.REASON_INVALID_MODEL_TIMEZONE, ex.reasonCode());
    }

    @Test
    @DisplayName("Explicit registry can exclude built-ins")
    void testExplicitRegistryWithoutBuiltIns() {
        TemporalTimezonePolicy custom = new TemporalTimezonePolicy() {
            @Override
            public String id() {
                return "CUSTOM_POLICY";
            }

            @Override
            public ZoneId resolveZoneId(TemporalRuntimeConfig runtimeConfig) {
                return ZoneId.of("UTC");
            }
        };

        TemporalTimezonePolicyRegistry registry = new TemporalTimezonePolicyRegistry(List.of(custom), false);
        assertNotNull(registry.policy("CUSTOM_POLICY"));
        assertNull(registry.policy(TemporalTimezonePolicyRegistry.POLICY_UTC));
    }

    @Test
    @DisplayName("Explicit registry can include built-ins")
    void testExplicitRegistryWithBuiltIns() {
        TemporalTimezonePolicy custom = new TemporalTimezonePolicy() {
            @Override
            public String id() {
                return "CUSTOM_POLICY";
            }

            @Override
            public ZoneId resolveZoneId(TemporalRuntimeConfig runtimeConfig) {
                return ZoneId.of("UTC");
            }
        };

        TemporalTimezonePolicyRegistry registry = new TemporalTimezonePolicyRegistry(List.of(custom), true);
        assertNotNull(registry.policy("CUSTOM_POLICY"));
        assertNotNull(registry.policy(TemporalTimezonePolicyRegistry.POLICY_UTC));
        assertNotNull(registry.policy(TemporalTimezonePolicyRegistry.POLICY_MODEL_TIMEZONE));
    }

    @Test
    @DisplayName("Registry rejects null policy entries")
    void testRegistryRejectsNullEntry() {
        assertThrows(
                NullPointerException.class,
                () -> new TemporalTimezonePolicyRegistry(java.util.Arrays.asList((TemporalTimezonePolicy) null), false)
        );
    }

    @Test
    @DisplayName("Registry rejects blank policy id")
    void testRegistryRejectsBlankPolicyId() {
        TemporalTimezonePolicy blank = new TemporalTimezonePolicy() {
            @Override
            public String id() {
                return "   ";
            }

            @Override
            public ZoneId resolveZoneId(TemporalRuntimeConfig runtimeConfig) {
                return ZoneId.of("UTC");
            }
        };

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new TemporalTimezonePolicyRegistry(List.of(blank), false)
        );
        assertTrue(ex.getMessage().contains("policy.id"));
    }
}
