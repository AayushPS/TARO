package org.Aayush.routing.execution;

import org.Aayush.routing.core.MatrixRequest;
import org.Aayush.routing.core.MatrixResponse;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.core.RouteResponse;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Atomic-swap runtime manager for execution-profile changes.
 *
 * <p>Each request captures the active router once at entry, so in-flight requests
 * remain pinned to the router instance that accepted them.</p>
 */
public final class RouterRuntimeManager implements ExecutionProfileAwareRouter, ExecutionConfigAdminService {
    private final RouterRuntimeFactory routerRuntimeFactory;
    private final AtomicReference<ExecutionProfileAwareRouter> activeRouter;

    /**
     * Creates a manager from an initial router instance and a runtime factory.
     */
    public RouterRuntimeManager(
            ExecutionProfileAwareRouter initialRouter,
            RouterRuntimeFactory routerRuntimeFactory
    ) {
        this.routerRuntimeFactory = Objects.requireNonNull(routerRuntimeFactory, "routerRuntimeFactory");
        this.activeRouter = new AtomicReference<>(Objects.requireNonNull(initialRouter, "initialRouter"));
    }

    /**
     * Creates a manager by building the initial router from the provided config.
     */
    public RouterRuntimeManager(
            RouterRuntimeFactory routerRuntimeFactory,
            ExecutionRuntimeConfig initialExecutionRuntimeConfig
    ) {
        this(
                Objects.requireNonNull(routerRuntimeFactory, "routerRuntimeFactory")
                        .create(Objects.requireNonNull(initialExecutionRuntimeConfig, "initialExecutionRuntimeConfig")),
                routerRuntimeFactory
        );
    }

    /**
     * Returns the currently active execution profile.
     */
    @Override
    public ResolvedExecutionProfileContext currentExecutionProfileContext() {
        return activeRouter.get().executionProfileContext();
    }

    /**
     * Returns the currently active execution profile.
     */
    @Override
    public ResolvedExecutionProfileContext executionProfileContext() {
        return currentExecutionProfileContext();
    }

    /**
     * Validates one candidate execution config without swapping the active router.
     */
    @Override
    public ResolvedExecutionProfileContext validateExecutionRuntimeConfig(ExecutionRuntimeConfig executionRuntimeConfig) {
        return routerRuntimeFactory.create(executionRuntimeConfig).executionProfileContext();
    }

    /**
     * Applies one candidate execution config by atomically swapping in a new router.
     */
    @Override
    public ResolvedExecutionProfileContext applyExecutionRuntimeConfig(ExecutionRuntimeConfig executionRuntimeConfig) {
        ExecutionProfileAwareRouter candidate = routerRuntimeFactory.create(executionRuntimeConfig);
        activeRouter.set(candidate);
        return candidate.executionProfileContext();
    }

    /**
     * Delegates route execution to the active router snapshot.
     */
    @Override
    public RouteResponse route(RouteRequest request) {
        ExecutionProfileAwareRouter router = activeRouter.get();
        return router.route(request);
    }

    /**
     * Delegates matrix execution to the active router snapshot.
     */
    @Override
    public MatrixResponse matrix(MatrixRequest request) {
        ExecutionProfileAwareRouter router = activeRouter.get();
        return router.matrix(request);
    }
}
