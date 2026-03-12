package org.Aayush.routing.execution;

import org.Aayush.routing.core.RouterService;

/**
 * Router contract that exposes the bound execution profile.
 */
public interface ExecutionProfileAwareRouter extends RouterService {
    /**
     * Returns the immutable execution profile bound to this router instance.
     */
    ResolvedExecutionProfileContext executionProfileContext();
}
