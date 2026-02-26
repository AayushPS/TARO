package org.Aayush.routing.traits.transition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 17 TransitionTraitCatalog Tests")
class TransitionTraitCatalogTest {

    @Test
    @DisplayName("Default catalog exposes NODE_BASED and EDGE_BASED traits")
    void testDefaultCatalogBuiltIns() {
        TransitionTraitCatalog catalog = TransitionTraitCatalog.defaultCatalog();
        assertNotNull(catalog.trait(TransitionTraitCatalog.TRAIT_NODE_BASED));
        assertNotNull(catalog.trait(TransitionTraitCatalog.TRAIT_EDGE_BASED));
        assertEquals(
                TransitionStrategyRegistry.STRATEGY_NODE_BASED,
                catalog.trait(TransitionTraitCatalog.TRAIT_NODE_BASED).strategyId()
        );
        assertEquals(
                TransitionStrategyRegistry.STRATEGY_EDGE_BASED,
                catalog.trait(TransitionTraitCatalog.TRAIT_EDGE_BASED).strategyId()
        );
    }

    @Test
    @DisplayName("Custom trait overrides built-in id deterministically")
    void testCustomTraitOverride() {
        TransitionTrait customEdgeTrait = new TransitionTrait() {
            @Override
            public String id() {
                return TransitionTraitCatalog.TRAIT_EDGE_BASED;
            }

            @Override
            public String strategyId() {
                return "CUSTOM_EDGE";
            }
        };

        TransitionTraitCatalog catalog = new TransitionTraitCatalog(List.of(customEdgeTrait));
        assertTrue(catalog.traitIds().contains(TransitionTraitCatalog.TRAIT_EDGE_BASED));
        assertEquals("CUSTOM_EDGE", catalog.trait(TransitionTraitCatalog.TRAIT_EDGE_BASED).strategyId());
    }

    @Test
    @DisplayName("Null trait lookup returns null")
    void testNullLookupReturnsNull() {
        TransitionTraitCatalog catalog = TransitionTraitCatalog.defaultCatalog();
        assertNull(catalog.trait(null));
    }

    @Test
    @DisplayName("Null custom trait collection falls back to built-ins")
    void testNullCustomCollectionUsesBuiltIns() {
        TransitionTraitCatalog catalog = new TransitionTraitCatalog((Collection<? extends TransitionTrait>) null);
        assertNotNull(catalog.trait(TransitionTraitCatalog.TRAIT_NODE_BASED));
        assertNotNull(catalog.trait(TransitionTraitCatalog.TRAIT_EDGE_BASED));
    }

    @Test
    @DisplayName("Explicit catalog can exclude built-ins")
    void testExplicitCatalogWithoutBuiltIns() {
        TransitionTrait customOnly = new TransitionTrait() {
            @Override
            public String id() {
                return "CUSTOM";
            }

            @Override
            public String strategyId() {
                return "CUSTOM_STRATEGY";
            }
        };

        TransitionTraitCatalog catalog = new TransitionTraitCatalog(List.of(customOnly), false);
        assertNotNull(catalog.trait("CUSTOM"));
        assertNull(catalog.trait(TransitionTraitCatalog.TRAIT_NODE_BASED));
        assertNull(catalog.trait(TransitionTraitCatalog.TRAIT_EDGE_BASED));
    }

    @Test
    @DisplayName("Catalog rejects null trait entries")
    void testCatalogRejectsNullTraitEntry() {
        assertThrows(
                NullPointerException.class,
                () -> new TransitionTraitCatalog(java.util.Arrays.asList((TransitionTrait) null), false)
        );
    }

    @Test
    @DisplayName("Catalog rejects blank trait id")
    void testCatalogRejectsBlankTraitId() {
        TransitionTrait blankId = new TransitionTrait() {
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
                () -> new TransitionTraitCatalog(List.of(blankId), false)
        );
        assertTrue(ex.getMessage().contains("trait.id"));
    }

    @Test
    @DisplayName("Catalog rejects blank strategy id")
    void testCatalogRejectsBlankStrategyId() {
        TransitionTrait blankStrategy = new TransitionTrait() {
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
                () -> new TransitionTraitCatalog(List.of(blankStrategy), false)
        );
        assertTrue(ex.getMessage().contains("trait.strategyId"));
    }
}
