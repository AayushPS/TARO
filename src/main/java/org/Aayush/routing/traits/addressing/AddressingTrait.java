package org.Aayush.routing.traits.addressing;

/**
 * Addressing-axis trait contract.
 *
 * <p>Traits declare supported address types and are resolved by id through
 * {@link AddressingTraitCatalog}.</p>
 */
public interface AddressingTrait {
    /**
     * Stable trait identifier used in requests.
     */
    String id();

    /**
     * Returns whether this trait supports the requested address type.
     */
    boolean supports(AddressType type);
}
