package org.Aayush.routing.traits.addressing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AddressingTraitCatalog Tests")
class AddressingTraitCatalogTest {

    @Test
    @DisplayName("Default catalog exposes built-ins and default trait")
    void testDefaultCatalogShape() {
        AddressingTraitCatalog catalog = AddressingTraitCatalog.defaultCatalog();

        assertNotNull(catalog.defaultTrait());
        assertEquals(AddressingTraitCatalog.TRAIT_DEFAULT, catalog.defaultTrait().id());
        assertNotNull(catalog.trait(AddressingTraitCatalog.TRAIT_DEFAULT));
        assertNotNull(catalog.trait(AddressingTraitCatalog.TRAIT_EXTERNAL_ID_ONLY));
        assertNull(catalog.trait(null));
        assertTrue(catalog.traitIds().contains(AddressingTraitCatalog.TRAIT_DEFAULT));
        assertTrue(catalog.traitIds().contains(AddressingTraitCatalog.TRAIT_EXTERNAL_ID_ONLY));
    }

    @Test
    @DisplayName("Custom trait can override built-in by id")
    void testCustomTraitOverrideBuiltIn() {
        AddressingTrait override = new SupportedTypeTrait(
                AddressingTraitCatalog.TRAIT_DEFAULT,
                Set.of(AddressType.COORDINATES)
        );
        AddressingTraitCatalog catalog = new AddressingTraitCatalog(List.of(override));

        AddressingTrait resolved = catalog.trait(AddressingTraitCatalog.TRAIT_DEFAULT);
        assertNotNull(resolved);
        assertTrue(resolved.supports(AddressType.COORDINATES));
        assertFalse(resolved.supports(AddressType.EXTERNAL_ID));
    }

    @Test
    @DisplayName("Null custom trait collection keeps built-in catalog shape")
    void testNullCustomTraitCollectionFallsBackToBuiltIns() {
        AddressingTraitCatalog catalog = new AddressingTraitCatalog((Collection<? extends AddressingTrait>) null);

        assertNotNull(catalog.trait(AddressingTraitCatalog.TRAIT_DEFAULT));
        assertNotNull(catalog.trait(AddressingTraitCatalog.TRAIT_EXTERNAL_ID_ONLY));
        assertEquals(AddressingTraitCatalog.TRAIT_DEFAULT, catalog.defaultTrait().id());
    }

    @Test
    @DisplayName("Explicit constructor rejects missing default trait id")
    void testExplicitCatalogRejectsMissingDefault() {
        AddressingTrait custom = new SupportedTypeTrait("COORD_ONLY", Set.of(AddressType.COORDINATES));

        assertThrows(
                IllegalArgumentException.class,
                () -> new AddressingTraitCatalog("MISSING", List.of(custom))
        );
    }

    @Test
    @DisplayName("Catalog constructors reject blank ids and null trait entries")
    void testCatalogRejectsBlankIdsAndNullTraits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AddressingTraitCatalog("   ", List.of(new SupportedTypeTrait("X", Set.of(AddressType.EXTERNAL_ID))))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new AddressingTraitCatalog("X", List.of(new SupportedTypeTrait("   ", Set.of(AddressType.EXTERNAL_ID))))
        );

        assertThrows(
                NullPointerException.class,
                () -> new AddressingTraitCatalog(Arrays.asList((AddressingTrait) null))
        );
    }

    private record SupportedTypeTrait(String id, Set<AddressType> supportedTypes) implements AddressingTrait {
        @Override
        public boolean supports(AddressType type) {
            return supportedTypes.contains(type);
        }
    }
}
