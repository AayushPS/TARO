package org.Aayush.routing.heuristic;

import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Landmark Heuristic Provider Tests")
class LandmarkHeuristicProviderTest {

    @Test
    @DisplayName("Provider binds to one goal and enforces node bounds")
    void testProviderBindingAndBounds() {
        RoutingFixtureFactory.Fixture fixture = createFixture();
        LandmarkStore store = new LandmarkStore(
                fixture.edgeGraph().nodeCount(),
                new int[]{0},
                new float[][]{{0.0f, 1.0f, 2.0f, 3.0f}},
                new float[][]{{0.0f, 1.0f, 2.0f, 3.0f}}
        );

        LandmarkHeuristicProvider provider = new LandmarkHeuristicProvider(fixture.edgeGraph(), store);
        GoalBoundHeuristic heuristic = provider.bindGoal(3);

        assertEquals(HeuristicType.LANDMARK, provider.type());
        assertEquals(store.lowerBound(0, 3), heuristic.estimateFromNode(0), 1.0e-9d);
        assertThrows(IllegalArgumentException.class, () -> provider.bindGoal(-1));
        assertThrows(IllegalArgumentException.class, () -> provider.bindGoal(4));
        assertThrows(IllegalArgumentException.class, () -> heuristic.estimateFromNode(-1));
        assertThrows(IllegalArgumentException.class, () -> heuristic.estimateFromNode(4));
    }

    @Test
    @DisplayName("Provider rejects node-count mismatches")
    void testProviderRejectsNodeCountMismatch() {
        RoutingFixtureFactory.Fixture fixture = createFixture();
        LandmarkStore mismatchedStore = new LandmarkStore(
                fixture.edgeGraph().nodeCount() - 1,
                new int[]{0},
                new float[][]{{0.0f, 1.0f, 2.0f}},
                new float[][]{{0.0f, 1.0f, 2.0f}}
        );

        assertThrows(IllegalArgumentException.class, () -> new LandmarkHeuristicProvider(fixture.edgeGraph(), mismatchedStore));
    }

    private RoutingFixtureFactory.Fixture createFixture() {
        return RoutingFixtureFactory.createFixture(
                4,
                new int[]{0, 1, 2, 3, 3},
                new int[]{1, 2, 3},
                new int[]{0, 1, 2},
                new float[]{1.0f, 1.0f, 1.0f},
                new int[]{1, 1, 1},
                new double[]{
                        0.0d, 0.0d,
                        1.0d, 0.0d,
                        2.0d, 0.0d,
                        3.0d, 0.0d
                },
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }
}
