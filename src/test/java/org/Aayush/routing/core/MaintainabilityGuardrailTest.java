package org.Aayush.routing.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Maintainability Guardrail Tests")
class MaintainabilityGuardrailTest {

    @Test
    @DisplayName("RouteCore orchestration stays within class-size maintainability budget")
    void testRouteCoreLineBudget() {
        assertMaxLines(Path.of("src/main/java/org/Aayush/routing/core/RouteCore.java"), 750);
    }

    @Test
    @DisplayName("AddressingTraitEngine stays within class-size maintainability budget")
    void testAddressingTraitEngineLineBudget() {
        assertMaxLines(Path.of("src/main/java/org/Aayush/routing/traits/addressing/AddressingTraitEngine.java"), 980);
    }

    @Test
    @DisplayName("NativeOneToManyMatrixPlanner stays within class-size maintainability budget")
    void testNativeOneToManyMatrixPlannerLineBudget() {
        assertMaxLines(Path.of("src/main/java/org/Aayush/routing/core/NativeOneToManyMatrixPlanner.java"), 650);
    }

    private void assertMaxLines(Path sourcePath, int maxLines) {
        assertTrue(Files.exists(sourcePath), "missing source file: " + sourcePath);
        try (Stream<String> lines = Files.lines(sourcePath)) {
            long lineCount = lines.count();
            assertTrue(
                    lineCount <= maxLines,
                    sourcePath + " has " + lineCount + " lines (max allowed " + maxLines + ")"
            );
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read source file: " + sourcePath, ex);
        }
    }
}
