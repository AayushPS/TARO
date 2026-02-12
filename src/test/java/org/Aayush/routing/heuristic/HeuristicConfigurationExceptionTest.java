package org.Aayush.routing.heuristic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("HeuristicConfigurationException Tests")
class HeuristicConfigurationExceptionTest {

    @Test
    @DisplayName("Three-arg constructor preserves reason code, message prefix, and cause")
    void testThreeArgConstructor() {
        IllegalStateException cause = new IllegalStateException("boom");
        HeuristicConfigurationException ex = new HeuristicConfigurationException(
                "TEST_REASON",
                "details",
                cause
        );

        assertEquals("TEST_REASON", ex.reasonCode());
        assertTrue(ex.getMessage().contains("[TEST_REASON] details"));
        assertSame(cause, ex.getCause());
    }

    @Test
    @DisplayName("Blank reason code is rejected deterministically")
    void testBlankReasonCodeRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new HeuristicConfigurationException(" ", "details")
        );
    }
}
