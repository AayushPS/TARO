package org.Aayush.routing.execution;

/**
 * Internal management surface for validating and applying
 * execution-profile updates.
 */
public interface ExecutionConfigAdminService {
    /**
     * Returns the currently active execution profile.
     *
     * @return currently active execution-profile context.
     */
    ResolvedExecutionProfileContext currentExecutionProfileContext();

    /**
     * Validates one candidate execution runtime config without applying it.
     *
     * @param executionRuntimeConfig candidate runtime config to validate.
     * @return validated execution-profile context without applying it.
     */
    ResolvedExecutionProfileContext validateExecutionRuntimeConfig(
            ExecutionRuntimeConfig executionRuntimeConfig);

    /**
     * Applies one candidate execution runtime config atomically.
     *
     * @param executionRuntimeConfig candidate runtime config to apply.
     * @return applied execution-profile context after the runtime update.
     */
    ResolvedExecutionProfileContext applyExecutionRuntimeConfig(
            ExecutionRuntimeConfig executionRuntimeConfig);
}
