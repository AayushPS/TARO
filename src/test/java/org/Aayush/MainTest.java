package org.Aayush;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

    @Test
    void testMainOutputsExpectedLines() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(buffer));
            Main.main(new String[0]);
        } finally {
            System.setOut(originalOut);
        }

        String output = buffer.toString();
        assertTrue(output.contains("Hello and welcome!"));
        assertTrue(output.contains("i = 1"));
        assertTrue(output.contains("i = 5"));
    }
}
