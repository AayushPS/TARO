package org.Aayush.app;

/**
 * Minimal application entry point used for local smoke runs.
 */
public class Main {
    /**
     * Launches the sample CLI routine.
     *
     * @param args command-line arguments.
     */
    public static void main(String[] args) {
        System.out.printf("Hello and welcome!");

        for (int i = 1; i <= 5; i++) {
            System.out.println("i = " + i);
        }
    }
}
