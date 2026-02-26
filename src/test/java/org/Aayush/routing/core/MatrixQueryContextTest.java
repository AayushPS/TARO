package org.Aayush.routing.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("MatrixQueryContext Tests")
class MatrixQueryContextTest {

    @Test
    @DisplayName("maxResolvedTargetCost tracks improvements without full scans on every call")
    void testMaxResolvedTargetCostTracksImprovedBests() {
        MatrixQueryContext context = new MatrixQueryContext();
        context.resetForRow(3);

        assertEquals(3, context.unresolvedTargets());
        assertEquals(0.0d, context.maxResolvedTargetCost(), 1e-9d);

        assertTrue(context.updateTargetBest(0, 10.0f, 100L));
        assertTrue(context.updateTargetBest(1, 7.0f, 90L));
        assertTrue(context.updateTargetBest(2, 12.0f, 110L));
        assertEquals(0, context.unresolvedTargets());
        assertEquals(12.0d, context.maxResolvedTargetCost(), 1e-9d);

        assertTrue(context.updateTargetBest(2, 6.0f, 95L));
        assertEquals(10.0d, context.maxResolvedTargetCost(), 1e-9d);

        assertTrue(context.updateTargetBest(0, 5.0f, 85L));
        assertEquals(7.0d, context.maxResolvedTargetCost(), 1e-9d);

        assertTrue(!context.updateTargetBest(1, 7.0f, 95L));
        assertEquals(7.0d, context.maxResolvedTargetCost(), 1e-9d);
    }

    @Test
    @DisplayName("resetForRow releases active-label edge containers to avoid thread-local graph-scale retention")
    void testResetForRowReleasesActiveEdgeContainers() {
        MatrixQueryContext context = new MatrixQueryContext();
        context.resetForRow(1);

        for (int edgeId = 0; edgeId < 2_048; edgeId++) {
            context.activeLabelsForEdge(edgeId).add(edgeId);
        }
        assertEquals(2_048, activeEdgeContainerCount(context));

        context.resetForRow(1);

        assertEquals(0, activeEdgeContainerCount(context));
    }

    private int activeEdgeContainerCount(MatrixQueryContext context) {
        try {
            Field field = MatrixQueryContext.class.getDeclaredField("activeLabelsByEdge");
            field.setAccessible(true);
            Object map = field.get(context);
            return (Integer) map.getClass().getMethod("size").invoke(map);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("failed to inspect MatrixQueryContext active labels", ex);
        }
    }
}
