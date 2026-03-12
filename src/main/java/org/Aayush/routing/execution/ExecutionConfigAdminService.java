package org.Aayush.routing.execution;

/**
 * Internal management surface for validating and applying execution-profile updates.
 */
public interface ExecutionConfigAdminService {
    /**
     * Returns the currently active execution profile.
     */
    ResolvedExecutionProfileContext currentExecutionProfileContext();

    /**
     * Validates one candidate execution runtime config without applying it.
     */
    ResolvedExecutionProfileContext validateExecutionRuntimeConfig(ExecutionRuntimeConfig executionRuntimeConfig);

    /**
     * Applies one candidate execution runtime config atomically.
     */
    ResolvedExecutionProfileContext applyExecutionRuntimeConfig(ExecutionRuntimeConfig executionRuntimeConfig);
}
