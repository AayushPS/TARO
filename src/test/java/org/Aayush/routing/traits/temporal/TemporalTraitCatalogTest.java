package org.Aayush.routing.traits.temporal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 16 TemporalTraitCatalog Tests")
class TemporalTraitCatalogTest {

    @Test
    @DisplayName("Default catalog exposes LINEAR and CALENDAR traits")
    void testDefaultCatalogBuiltIns() {
        TemporalTraitCatalog catalog = TemporalTraitCatalog.defaultCatalog();
        assertNotNull(catalog.trait(TemporalTraitCatalog.TRAIT_LINEAR));
        assertNotNull(catalog.trait(TemporalTraitCatalog.TRAIT_CALENDAR));
        assertEquals(TemporalStrategyRegistry.STRATEGY_LINEAR,
                catalog.trait(TemporalTraitCatalog.TRAIT_LINEAR).strategyId());
        assertEquals(TemporalStrategyRegistry.STRATEGY_CALENDAR,
                catalog.trait(TemporalTraitCatalog.TRAIT_CALENDAR).strategyId());
    }

    @Test
    @DisplayName("Custom trait overrides built-in id deterministically")
    void testCustomTraitOverride() {
        TemporalTrait customLinear = new TemporalTrait() {
            @Override
            public String id() {
                return TemporalTraitCatalog.TRAIT_LINEAR;
            }

            @Override
            public String strategyId() {
                return "CUSTOM_LINEAR";
            }
        };

        TemporalTraitCatalog catalog = new TemporalTraitCatalog(List.of(customLinear));
        assertTrue(catalog.traitIds().contains(TemporalTraitCatalog.TRAIT_LINEAR));
        assertEquals("CUSTOM_LINEAR", catalog.trait(TemporalTraitCatalog.TRAIT_LINEAR).strategyId());
    }

    @Test
    @DisplayName("Null trait lookup returns null")
    void testNullLookupReturnsNull() {
        TemporalTraitCatalog catalog = TemporalTraitCatalog.defaultCatalog();
        assertNull(catalog.trait(null));
    }

    @Test
    @DisplayName("Null custom trait collection falls back to built-ins")
    void testNullCustomCollectionUsesBuiltIns() {
        TemporalTraitCatalog catalog = new TemporalTraitCatalog((Collection<? extends TemporalTrait>) null);
        assertNotNull(catalog.trait(TemporalTraitCatalog.TRAIT_LINEAR));
        assertNotNull(catalog.trait(TemporalTraitCatalog.TRAIT_CALENDAR));
    }

    @Test
    @DisplayName("Explicit catalog can exclude built-ins")
    void testExplicitCatalogWithoutBuiltIns() {
        TemporalTrait customOnly = new TemporalTrait() {
            @Override
            public String id() {
                return "CUSTOM";
            }

            @Override
            public String strategyId() {
                return "CUSTOM_STRATEGY";
            }
        };

        TemporalTraitCatalog catalog = new TemporalTraitCatalog(List.of(customOnly), false);
        assertNotNull(catalog.trait("CUSTOM"));
        assertNull(catalog.trait(TemporalTraitCatalog.TRAIT_LINEAR));
        assertNull(catalog.trait(TemporalTraitCatalog.TRAIT_CALENDAR));
    }

    @Test
    @DisplayName("Explicit catalog can include built-ins")
    void testExplicitCatalogWithBuiltIns() {
        TemporalTrait customOnly = new TemporalTrait() {
            @Override
            public String id() {
                return "CUSTOM";
            }

            @Override
            public String strategyId() {
                return "CUSTOM_STRATEGY";
            }
        };

        TemporalTraitCatalog catalog = new TemporalTraitCatalog(List.of(customOnly), true);
        assertNotNull(catalog.trait("CUSTOM"));
        assertNotNull(catalog.trait(TemporalTraitCatalog.TRAIT_LINEAR));
        assertNotNull(catalog.trait(TemporalTraitCatalog.TRAIT_CALENDAR));
    }

    @Test
    @DisplayName("Catalog rejects null trait entries")
    void testCatalogRejectsNullTraitEntry() {
        assertThrows(
                NullPointerException.class,
                () -> new TemporalTraitCatalog(java.util.Arrays.asList((TemporalTrait) null), false)
        );
    }

    @Test
    @DisplayName("Catalog rejects blank trait id")
    void testCatalogRejectsBlankTraitId() {
        TemporalTrait blankId = new TemporalTrait() {
            @Override
            public String id() {
                return "   ";
            }

            @Override
            public String strategyId() {
                return "ANY";
            }
        };
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new TemporalTraitCatalog(List.of(blankId), false)
        );
        assertTrue(ex.getMessage().contains("trait.id"));
    }

    @Test
    @DisplayName("Catalog rejects blank strategy id")
    void testCatalogRejectsBlankStrategyId() {
        TemporalTrait blankStrategy = new TemporalTrait() {
            @Override
            public String id() {
                return "CUSTOM";
            }

            @Override
            public String strategyId() {
                return "   ";
            }
        };
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new TemporalTraitCatalog(List.of(blankStrategy), false)
        );
        assertTrue(ex.getMessage().contains("trait.strategyId"));
    }
}
