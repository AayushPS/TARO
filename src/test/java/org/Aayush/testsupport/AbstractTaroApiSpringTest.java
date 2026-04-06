package org.Aayush.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.Aayush.api.CallerScopedRetainedResultRegistry;
import org.Aayush.api.FutureApiTestConfiguration;
import org.Aayush.api.OperationalMetricsService;
import org.Aayush.routing.future.InMemoryEphemeralMatrixResultStore;
import org.Aayush.routing.future.InMemoryEphemeralRouteResultStore;
import org.Aayush.routing.topology.TopologyReloadCoordinator;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

public abstract class AbstractTaroApiSpringTest {
    protected static final Instant BASE_INSTANT = FutureApiTestConfiguration.BASE_INSTANT;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected FutureApiTestConfiguration.ApiMutableClock apiMutableClock;

    @Autowired
    protected TopologyReloadCoordinator topologyReloadCoordinator;

    @Autowired
    private InMemoryEphemeralRouteResultStore routeResultStore;

    @Autowired
    private InMemoryEphemeralMatrixResultStore matrixResultStore;

    @Autowired
    private CallerScopedRetainedResultRegistry retainedResultRegistry;

    @Autowired
    private OperationalMetricsService operationalMetricsService;

    @BeforeEach
    protected final void resetBaseApiState() {
        apiMutableClock.set(BASE_INSTANT);
        routeResultStore.invalidate(resultSet -> true);
        matrixResultStore.invalidate(resultSet -> true);
        retainedResultRegistry.clear();
        operationalMetricsService.clear();
        topologyReloadCoordinator.applyReload(FutureApiTestConfiguration.initialSnapshot());
        resetAdditionalState();
    }

    protected void resetAdditionalState() {
    }
}
