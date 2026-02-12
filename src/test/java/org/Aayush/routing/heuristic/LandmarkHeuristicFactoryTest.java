package org.Aayush.routing.heuristic;

import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 12 LANDMARK HeuristicFactory Tests")
class LandmarkHeuristicFactoryTest {

    @Test
    @DisplayName("LANDMARK heuristic requires LandmarkStore")
    void testLandmarkStoreRequired() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixture();
        HeuristicConfigurationException ex = assertThrows(
                HeuristicConfigurationException.class,
                () -> HeuristicFactory.create(
                        HeuristicType.LANDMARK,
                        fixture.edgeGraph(),
                        fixture.profileStore(),
                        fixture.costEngine()
                )
        );
        assertEquals(HeuristicFactory.REASON_LANDMARK_STORE_REQUIRED, ex.reasonCode());
    }

    @Test
    @DisplayName("LANDMARK heuristic rejects node-count mismatches")
    void testLandmarkNodeCountMismatch() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixture();
        LandmarkStore mismatched = new LandmarkStore(
                3,
                new int[]{0},
                new float[][]{{0.0f, 1.0f, 2.0f}},
                new float[][]{{0.0f, 1.0f, 2.0f}}
        );

        HeuristicConfigurationException ex = assertThrows(
                HeuristicConfigurationException.class,
                () -> HeuristicFactory.create(
                        HeuristicType.LANDMARK,
                        fixture.edgeGraph(),
                        fixture.profileStore(),
                        fixture.costEngine(),
                        mismatched
                )
        );
        assertEquals(HeuristicFactory.REASON_LANDMARK_NODE_COUNT_MISMATCH, ex.reasonCode());
    }

    @Test
    @DisplayName("LANDMARK heuristic requires compatibility signature on store")
    void testLandmarkSignatureRequired() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixture();
        LandmarkStore unsignedStore = new LandmarkStore(
                fixture.edgeGraph().nodeCount(),
                new int[]{0},
                new float[][]{{0.0f, 1.0f, 2.0f, 3.0f, 4.0f}},
                new float[][]{{4.0f, 3.0f, 2.0f, 1.0f, 0.0f}}
        );

        HeuristicConfigurationException ex = assertThrows(
                HeuristicConfigurationException.class,
                () -> HeuristicFactory.create(
                        HeuristicType.LANDMARK,
                        fixture.edgeGraph(),
                        fixture.profileStore(),
                        fixture.costEngine(),
                        unsignedStore
                )
        );
        assertEquals(HeuristicFactory.REASON_LANDMARK_SIGNATURE_REQUIRED, ex.reasonCode());
    }

    @Test
    @DisplayName("LANDMARK heuristic returns admissible estimates on simple chain")
    void testLandmarkAdmissibilityOnLinearGraph() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixture();
        LandmarkArtifact artifact = LandmarkPreprocessor.preprocess(
                fixture.edgeGraph(),
                fixture.profileStore(),
                LandmarkPreprocessorConfig.builder()
                        .landmarkCount(2)
                        .selectionSeed(42L)
                        .build()
        );
        LandmarkStore store = LandmarkStore.fromArtifact(artifact);

        HeuristicProvider provider = HeuristicFactory.create(
                HeuristicType.LANDMARK,
                fixture.edgeGraph(),
                fixture.profileStore(),
                fixture.costEngine(),
                store
        );
        assertEquals(HeuristicType.LANDMARK, provider.type());

        GoalBoundHeuristic bound = provider.bindGoal(4);
        for (int node = 0; node <= 4; node++) {
            double estimate = bound.estimateFromNode(node);
            double shortest = 4 - node;
            assertTrue(estimate >= 0.0d);
            assertTrue(estimate <= shortest + 1e-6, "inadmissible at node " + node);
        }
    }

    @Test
    @DisplayName("LANDMARK heuristic rejects same-node-count artifacts from different graph contracts")
    void testLandmarkSignatureMismatchRejected() {
        RoutingFixtureFactory.Fixture fixtureA = RoutingFixtureFactory.createFixture(
                4,
                new int[]{0, 2, 3, 4, 4},
                new int[]{1, 2, 3, 3},
                new int[]{0, 0, 1, 2},
                new float[]{1.0f, 1.0f, 100.0f, 1.0f},
                new int[]{1, 1, 1, 1},
                null,
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
        RoutingFixtureFactory.Fixture fixtureB = RoutingFixtureFactory.createFixture(
                4,
                new int[]{0, 2, 3, 4, 4},
                new int[]{1, 2, 3, 3},
                new int[]{0, 0, 1, 2},
                new float[]{1.0f, 1.0f, 1.0f, 10.0f},
                new int[]{1, 1, 1, 1},
                null,
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );

        LandmarkStore wrongStore = LandmarkStore.fromArtifact(
                LandmarkPreprocessor.preprocess(
                        fixtureA.edgeGraph(),
                        fixtureA.profileStore(),
                        LandmarkPreprocessorConfig.builder()
                                .landmarkCount(4)
                                .selectionSeed(9L)
                                .build()
                )
        );

        HeuristicConfigurationException ex = assertThrows(
                HeuristicConfigurationException.class,
                () -> HeuristicFactory.create(
                        HeuristicType.LANDMARK,
                        fixtureB.edgeGraph(),
                        fixtureB.profileStore(),
                        fixtureB.costEngine(),
                        wrongStore
                )
        );
        assertEquals(HeuristicFactory.REASON_LANDMARK_SIGNATURE_MISMATCH, ex.reasonCode());
    }

    private RoutingFixtureFactory.Fixture createLinearFixture() {
        return RoutingFixtureFactory.createFixture(
                5,
                new int[]{0, 1, 2, 3, 4, 4},
                new int[]{1, 2, 3, 4},
                new int[]{0, 1, 2, 3},
                new float[]{1.0f, 1.0f, 1.0f, 1.0f},
                new int[]{1, 1, 1, 1},
                new double[]{
                        0.0, 0.0,
                        1.0, 0.0,
                        2.0, 0.0,
                        3.0, 0.0,
                        4.0, 0.0
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
