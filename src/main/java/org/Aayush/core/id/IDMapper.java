package org.Aayush.core.id;

import lombok.experimental.StandardException;

import java.util.Map;

/**
 * Bidirectional mapping contract between external string ids and internal dense integer ids.
 */
public interface IDMapper {

    /**
     * Converts an external String ID to an internal integer index.
     * @param externalId The client-facing ID.
     * @return The internal integer index.
     * @throws UnknownIDException If the ID is not found.
     */
    int toInternal(String externalId) throws UnknownIDException;

    /**
     * Converts an internal integer index to an external String ID.
     * @param internalId The internal engine index.
     * @return The client-facing String ID.
     * @throws IndexOutOfBoundsException If the internal ID is invalid.
     */
    String toExternal(int internalId);

    /**
     * Checks whether an external id has a mapped internal id.
     *
     * @param externalId external id to test.
     * @return true when the external id is present.
     */
    boolean containsExternal(String externalId);

    /**
     * Checks whether an internal id is within mapper bounds.
     *
     * @param internalId internal id to test.
     * @return true when the internal id is present.
     */
    boolean containsInternal(int internalId);

    /**
     * Returns number of id pairs in the mapping.
     *
     * @return total mapping size.
     */
    int size();

    /**
     * Exception thrown when an external ID cannot be found in the mapping.
     */
    @StandardException
    class UnknownIDException extends RuntimeException {
    }

    /**
     * Factory method to create the default immutable implementation.
     * Uses the standalone FastUtilIDMapper class.
     *
     * @param mappings A map of External ID -> Internal ID.
     * Indices must be a dense range from 0 to size-1.
     * @return An immutable IDMapper instance.
     */
    static IDMapper createImmutable(Map<String, Integer> mappings) {
        return new FastUtilIDMapper(mappings);
    }
}
