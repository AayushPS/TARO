package org.Aayush.Utils;

/**
 * Thrown when attempting to extract from an empty SearchQueue.
 */
public class EmptyQueueException extends IllegalStateException {
    public EmptyQueueException(String message) {
        super(message);
    }
}
