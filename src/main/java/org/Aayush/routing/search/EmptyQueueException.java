package org.Aayush.routing.search;

/**
 * Thrown when attempting to extract from an empty SearchQueue.
 */
public class EmptyQueueException extends IllegalStateException {
    public EmptyQueueException(String message) {
        super(message);
    }
}
