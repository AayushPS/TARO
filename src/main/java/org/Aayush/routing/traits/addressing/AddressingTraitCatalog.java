package org.Aayush.routing.traits.addressing;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable addressing-trait registry for Stage 15 addressing resolution.
 */
public final class AddressingTraitCatalog {
    public static final String TRAIT_DEFAULT = "DEFAULT";
    public static final String TRAIT_EXTERNAL_ID_ONLY = "EXTERNAL_ID_ONLY";

    private static final AddressingTrait DEFAULT_TRAIT_IMPL =
            new SupportedTypeTrait(TRAIT_DEFAULT, Set.of(AddressType.EXTERNAL_ID, AddressType.COORDINATES));
    private static final AddressingTrait EXTERNAL_ONLY_TRAIT_IMPL =
            new SupportedTypeTrait(TRAIT_EXTERNAL_ID_ONLY, Set.of(AddressType.EXTERNAL_ID));

    private final Map<String, AddressingTrait> traitsById;
    private final String defaultTraitId;

    /**
     * Creates a catalog with built-in traits only.
     */
    public AddressingTraitCatalog() {
        this(TRAIT_DEFAULT, defaultTraits());
    }

    /**
     * Creates a catalog by merging built-ins with custom traits.
     *
     * <p>Custom trait ids override built-ins when ids collide.</p>
     */
    public AddressingTraitCatalog(Collection<? extends AddressingTrait> customTraits) {
        this(TRAIT_DEFAULT, mergeWithBuiltIns(customTraits));
    }

    /**
     * Creates a fully explicit catalog.
     */
    public AddressingTraitCatalog(String defaultTraitId, Collection<? extends AddressingTrait> traits) {
        String normalizedDefaultTraitId = normalizeRequiredId(defaultTraitId, "defaultTraitId");
        LinkedHashMap<String, AddressingTrait> map = new LinkedHashMap<>();
        for (AddressingTrait trait : Objects.requireNonNull(traits, "traits")) {
            AddressingTrait nonNullTrait = Objects.requireNonNull(trait, "trait");
            String id = normalizeRequiredId(nonNullTrait.id(), "trait.id");
            map.put(id, nonNullTrait);
        }
        if (!map.containsKey(normalizedDefaultTraitId)) {
            throw new IllegalArgumentException("defaultTraitId is not present in traits: " + normalizedDefaultTraitId);
        }
        this.defaultTraitId = normalizedDefaultTraitId;
        this.traitsById = Map.copyOf(map);
    }

    /**
     * Returns configured default trait.
     */
    public AddressingTrait defaultTrait() {
        return traitsById.get(defaultTraitId);
    }

    /**
     * Returns trait by id (case-sensitive), or null when not registered.
     */
    public AddressingTrait trait(String traitId) {
        if (traitId == null) {
            return null;
        }
        return traitsById.get(traitId);
    }

    /**
     * Returns immutable view of registered trait ids.
     */
    public Set<String> traitIds() {
        return traitsById.keySet();
    }

    /**
     * Returns default catalog singleton shape.
     */
    public static AddressingTraitCatalog defaultCatalog() {
        return new AddressingTraitCatalog();
    }

    private static Collection<? extends AddressingTrait> defaultTraits() {
        return java.util.List.of(DEFAULT_TRAIT_IMPL, EXTERNAL_ONLY_TRAIT_IMPL);
    }

    private static Collection<? extends AddressingTrait> mergeWithBuiltIns(Collection<? extends AddressingTrait> customTraits) {
        LinkedHashMap<String, AddressingTrait> merged = new LinkedHashMap<>();
        for (AddressingTrait trait : defaultTraits()) {
            merged.put(trait.id(), trait);
        }
        if (customTraits != null) {
            for (AddressingTrait trait : customTraits) {
                AddressingTrait nonNullTrait = Objects.requireNonNull(trait, "trait");
                merged.put(normalizeRequiredId(nonNullTrait.id(), "trait.id"), nonNullTrait);
            }
        }
        return merged.values();
    }

    private static String normalizeRequiredId(String id, String fieldName) {
        String normalized = Objects.requireNonNull(id, fieldName).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must be non-blank");
        }
        return normalized;
    }

    private record SupportedTypeTrait(String id, Set<AddressType> supportedTypes) implements AddressingTrait {
        private SupportedTypeTrait {
            Objects.requireNonNull(id, "id");
            supportedTypes = Set.copyOf(Objects.requireNonNull(supportedTypes, "supportedTypes"));
        }

        @Override
        public boolean supports(AddressType type) {
            return supportedTypes.contains(type);
        }
    }
}
