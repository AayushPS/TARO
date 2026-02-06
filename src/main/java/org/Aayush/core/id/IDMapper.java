package org.Aayush.core.id;

import java.util.Map;

/**
 * Stage 1: ID Translation Layer (Interface)
 * Defines the contract for bidirectional mapping between External IDs (String) 
 * and Internal IDs (int).
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

    boolean containsExternal(String externalId);
    boolean containsInternal(int internalId);

    int size();

    /**
     * Exception thrown when an external ID cannot be found in the mapping.
     */
    class UnknownIDException extends RuntimeException {
        public UnknownIDException(String message) {
            super(message);
        }
    }

    /**
     * Factory method to create the default immutable implementation.
     * Uses the standalone FastUtilIDMapper class.
     * * @param mappings A map of External ID -> Internal ID. 
     * Indices must be a dense range from 0 to size-1.
     * @return An immutable IDMapper instance.
     */
    static IDMapper createImmutable(Map<String, Integer> mappings) {
        return new FastUtilIDMapper(mappings);
    }
}