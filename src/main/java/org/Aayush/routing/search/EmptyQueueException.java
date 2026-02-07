package org.Aayush.routing.search;

import lombok.experimental.StandardException;

/**
 * Thrown when attempting to extract from an empty SearchQueue.
 */
@StandardException
public class EmptyQueueException extends IllegalStateException {
}
