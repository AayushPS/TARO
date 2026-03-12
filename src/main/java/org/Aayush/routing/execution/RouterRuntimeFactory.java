package org.Aayush.routing.execution;

/**
 * Factory for creating immutable routers bound to one execution profile.
 */
@FunctionalInterface
public interface RouterRuntimeFactory {
    /**
     * Creates a new immutable router bound to the provided execution config.
     */
    ExecutionProfileAwareRouter create(ExecutionRuntimeConfig executionRuntimeConfig);
}
