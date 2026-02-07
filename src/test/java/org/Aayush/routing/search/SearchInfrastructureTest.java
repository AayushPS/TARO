package org.Aayush.routing.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Search Infrastructure Tests")
class SearchInfrastructureTest {

    @Nested
    @DisplayName("1. SearchState Logic")
    class SearchStateTests {

        @Test
        @DisplayName("Comparison: Lower Cost is Better")
        void testCostComparison() {
            SearchState s1 = new SearchState();
            s1.set(1, 100L, 10.0f, 0);

            SearchState s2 = new SearchState();
            s2.set(1, 100L, 20.0f, 0); // Higher cost

            assertTrue(s1.compareTo(s2) < 0, "State with cost 10 should be smaller than cost 20");
            assertTrue(s2.compareTo(s1) > 0, "State with cost 20 should be larger than cost 10");
        }

        @Test
        @DisplayName("Comparison: Tie-Breaking by Time")
        void testTimeComparison() {
            SearchState s1 = new SearchState();
            s1.set(1, 100L, 10.0f, 0);

            SearchState s2 = new SearchState();
            s2.set(1, 200L, 10.0f, 0); // Same cost, later time

            assertEquals(0, s1.compareTo(new SearchState() {{ set(1, 100L, 10.0f, 0); }}), "Exact same values should be equal");
            assertTrue(s1.compareTo(s2) < 0, "Earlier time should be preferred (smaller)");
        }

        @Test
        @DisplayName("Comparison: Stable Tie-Breaking by Edge ID")
        void testEdgeIdTieBreak() {
            SearchState s1 = new SearchState();
            s1.set(1, 100L, 10.0f, 0);

            SearchState s2 = new SearchState();
            s2.set(2, 100L, 10.0f, 0);

            assertTrue(s1.compareTo(s2) < 0, "Lower edge ID should be preferred for exact cost/time ties");
            assertTrue(s2.compareTo(s1) > 0, "Higher edge ID should be deprioritized for exact cost/time ties");
        }

        @Test
        @DisplayName("Concurrent Decrease-Key on Same Edge")
        void testMultipleDecreaseKey() {
            SearchQueue queue = new SearchQueue(10, 10);

            // Simulate finding multiple paths to same edge
            queue.insert(5, 100, 50.0f, 0);  // First path
            queue.insert(5, 150, 30.0f, 1);  // Better path
            queue.insert(5, 200, 10.0f, 2);  // Even better path

            assertEquals(1, queue.size(), "Should only have one entry for edge 5");

            SearchState s = queue.extractMin();
            assertEquals(10.0f, s.cost, "Should keep the best cost");
            assertEquals(2, s.predecessor, "Should keep the best predecessor");
            queue.recycle(s);
        }
    }

    @Nested
    @DisplayName("2. VisitedSet Logic")
    class VisitedSetTests {

        private VisitedSet visitedSet;

        @BeforeEach
        void setUp() {
            visitedSet = new VisitedSet(100);
        }

        @Test
        @DisplayName("Marking and checking visits")
        void testMarkAndCheck() {
            assertFalse(visitedSet.isVisited(50), "Should be unvisited initially");

            boolean firstMark = visitedSet.markVisited(50);
            assertTrue(firstMark, "First markVisited should return true");
            assertTrue(visitedSet.isVisited(50), "Should report true after marking");

            boolean secondMark = visitedSet.markVisited(50);
            assertFalse(secondMark, "Second markVisited should return false");
        }

        @Test
        @DisplayName("Clear resets state")
        void testClear() {
            visitedSet.markVisited(10);
            visitedSet.clear();
            assertFalse(visitedSet.isVisited(10), "Should be unvisited after clear");
        }
    }

    @Nested
    @DisplayName("3. SearchQueue (Min-Heap & Pooling)")
    class SearchQueueTests {

        @Test
        @DisplayName("Basic Insert and Extract Order")
        void testHeapOrdering() {
            SearchQueue queue = new SearchQueue(10, 10);
            queue.insert(1, 100, 50.0f, 0);
            queue.insert(2, 100, 10.0f, 0); // Cheapest
            queue.insert(3, 100, 30.0f, 0);

            SearchState s1 = queue.extractMin();
            assertEquals(10.0f, s1.cost, "First extracted should be 10.0");
            queue.recycle(s1);

            SearchState s2 = queue.extractMin();
            assertEquals(30.0f, s2.cost, "Second extracted should be 30.0");
            queue.recycle(s2);

            SearchState s3 = queue.extractMin();
            assertEquals(50.0f, s3.cost, "Third extracted should be 50.0");
            queue.recycle(s3);

            assertTrue(queue.isEmpty(), "Queue should be empty after extracting all");
        }

        @Test
        @DisplayName("Validation: Invalid MaxEdgeId")
        void testConstructorValidation() {
            assertThrows(IllegalArgumentException.class, () -> new SearchQueue(-1, 10));
            assertThrows(IllegalArgumentException.class, () -> new SearchQueue(10, 0));
        }

        @Test
        @DisplayName("Validation: Insert Out of Bounds")
        void testInsertValidation() {
            SearchQueue queue = new SearchQueue(10, 10);
            assertThrows(IllegalArgumentException.class, () -> queue.insert(-1, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> queue.insert(11, 0, 0, 0));
        }

        @Test
        @DisplayName("Validation: Extract from Empty")
        void testExtractEmpty() {
            SearchQueue queue = new SearchQueue(10, 10);
            assertThrows(EmptyQueueException.class, queue::extractMin);
        }

        @Test
        @DisplayName("Validation: Pool Exhaustion (Leak Detection)")
        void testPoolExhaustion() {
            // Capacity 2
            SearchQueue queue = new SearchQueue(10, 2);
            queue.insert(1, 0, 0, 0);
            
            // Extract but DO NOT recycle (simulate leak)
            queue.extractMin(); 
            
            queue.insert(2, 0, 0, 0);
            queue.extractMin(); // Now 2 states are leaked. Pool is empty.

            // Next insert requires a new object, but pool is empty
            IllegalStateException ex = assertThrows(IllegalStateException.class, 
                () -> queue.insert(3, 0, 0, 0));
            assertTrue(ex.getMessage().contains("Pool exhausted"), "Exception should mention pool exhaustion");
        }

        @Test
        @DisplayName("Clear recovers capacity even after leaked extracted states")
        void testClearRecoversAfterLeak() {
            SearchQueue queue = new SearchQueue(10, 2);
            queue.insert(1, 0, 1.0f, -1);

            // Leak one extracted state.
            queue.extractMin();

            queue.insert(2, 0, 2.0f, -1);
            queue.clear();

            // Queue should be fully reusable up to capacity after clear().
            assertDoesNotThrow(() -> {
                queue.insert(3, 0, 3.0f, -1);
                queue.insert(4, 0, 4.0f, -1);
            });
            assertEquals(2, queue.size());
        }

        @Test
        @DisplayName("Decrease-Key: Update Existing Path")
        void testDecreaseKey() {
            SearchQueue queue = new SearchQueue(10, 10);
            queue.insert(5, 100, 50.0f, 0);
            
            // Insert same edge ID with LOWER cost
            queue.insert(5, 100, 20.0f, 0);
            
            assertEquals(1, queue.size(), "Should update existing entry, not add duplicate");
            
            SearchState s = queue.extractMin();
            assertEquals(20.0f, s.cost, "Cost should be updated to 20.0");
            queue.recycle(s);
        }

        @Test
        @DisplayName("Decrease-Key: Ignore Worse Path")
        void testIgnoreWorsePath() {
            SearchQueue queue = new SearchQueue(10, 10);
            queue.insert(5, 100, 20.0f, 0);
            
            // Insert same edge ID with HIGHER cost
            queue.insert(5, 100, 50.0f, 0);
            
            SearchState s = queue.extractMin();
            assertEquals(20.0f, s.cost, "Should keep original lower cost");
            queue.recycle(s);
        }

        @Test
        @DisplayName("Regression: Extract + Reinsert Must Not Corrupt Queue Membership")
        void testExtractReinsertMembershipIntegrity() {
            SearchQueue queue = new SearchQueue(10, 10);
            queue.insert(1, 0, 1.0f, -1);
            queue.insert(2, 0, 2.0f, -1);

            SearchState first = queue.extractMin(); // edge 1
            queue.recycle(first);

            queue.insert(3, 0, 3.0f, -1);
            queue.insert(1, 0, 0.5f, -1); // Reinsert extracted edge with better cost

            assertEquals(3, queue.size(), "Queue should contain edges 1, 2, and 3");

            SearchState s1 = queue.extractMin();
            SearchState s2 = queue.extractMin();
            SearchState s3 = queue.extractMin();

            assertEquals(1, s1.edgeId, "First should be reinserted edge 1");
            assertEquals(2, s2.edgeId, "Second should be edge 2");
            assertEquals(3, s3.edgeId, "Third should be edge 3");

            queue.recycle(s1);
            queue.recycle(s2);
            queue.recycle(s3);
        }

        @Test
        @DisplayName("Clear and Reuse Across Queries")
        void testClearAndReuse() {
            SearchQueue queue = new SearchQueue(10, 10);

            queue.insert(1, 10, 10.0f, -1);
            queue.insert(2, 20, 20.0f, -1);
            queue.clear();

            assertTrue(queue.isEmpty(), "Queue should be empty after clear");
            assertEquals(0, queue.size(), "Size should be zero after clear");

            queue.insert(3, 30, 3.0f, -1);
            queue.insert(4, 40, 4.0f, -1);

            SearchState first = queue.extractMin();
            SearchState second = queue.extractMin();

            assertEquals(3, first.edgeId, "Queue should be reusable after clear");
            assertEquals(4, second.edgeId, "Second item should follow normal ordering");

            queue.recycle(first);
            queue.recycle(second);
        }
    }

    @Nested
    @DisplayName("4. Stress & Performance")
    class PerformanceTests {

        @Test
        @DisplayName("Stress: 50k Random Operations")
        void testRandomOperations() {
            int capacity = 1000;
            SearchQueue queue = new SearchQueue(capacity, capacity);
            Random rand = new Random(42);

            assertDoesNotThrow(() -> {
                for (int i = 0; i < 50_000; i++) {
                    boolean insert = rand.nextBoolean();
                    // Basic balancing to keep it running
                    if (queue.isEmpty()) insert = true;
                    if (queue.size() >= capacity) insert = false;

                    if (insert) {
                        queue.insert(rand.nextInt(capacity), 0, rand.nextFloat(), 0);
                    } else {
                        SearchState s = queue.extractMin();
                        queue.recycle(s);
                    }
                }
            });
        }

        @Test
        @DisplayName("Performance: 100k Insert/Extract")
        void testThroughput() {
            int N = 100_000;
            SearchQueue queue = new SearchQueue(N, N);
            long start = System.nanoTime();

            // 1. Fill (Worst-case reverse order to force 'swim' operations)
            for (int i = 0; i < N; i++) {
                queue.insert(i, i, (float)(N - i), 0);
            }

            // 2. Empty
            while (!queue.isEmpty()) {
                SearchState s = queue.extractMin();
                queue.recycle(s);
            }

            long durationMs = (System.nanoTime() - start) / 1_000_000;
            System.out.println("Performance Test Time: " + durationMs + " ms");

            assertTrue(durationMs < 1000, "Should handle 100k ops in under 1 second (Target: <500ms)");
        }
    }
}
