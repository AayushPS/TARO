import unittest
from Utils.IDMapper import IDMapper


class TestIDMapper(unittest.TestCase):

    def setUp(self):
        # Fresh mapper for each test
        self.mapper = IDMapper()

    def test_simple_ascii(self):
        id_ny = self.mapper.get_or_create("NewYork")
        id_la = self.mapper.get_or_create("LosAngeles")

        self.assertNotEqual(id_ny, id_la)
        self.assertEqual(self.mapper.to_external(id_ny), "NewYork")
        self.assertEqual(self.mapper.to_external(id_la), "LosAngeles")

    def test_unicode_strings(self):
        id_tokyo = self.mapper.get_or_create("Tōkyō_東京")
        self.assertEqual(self.mapper.to_external(id_tokyo), "Tōkyō_東京")

    def test_long_string(self):
        s = "A" * 2000
        id_long = self.mapper.get_or_create(s)
        self.assertEqual(self.mapper.to_external(id_long), s)

    def test_consistency(self):
        id1 = self.mapper.get_or_create("X")
        id2 = self.mapper.get_or_create("X")

        self.assertEqual(id1, id2)
        self.assertEqual(self.mapper.size(), 1)

    def test_error_handling(self):
        """Verify correct exceptions are raised for invalid IDs."""
        self.mapper.get_or_create("Existing")  # ID 0

        # Test to_internal with a non-existent external ID
        with self.assertRaises(KeyError):
            self.mapper.to_internal("NonExistent")

        # Test to_external with an out-of-bounds internal ID
        with self.assertRaises(IndexError):
            self.mapper.to_external(1)  # Only ID 0 exists
        with self.assertRaises(IndexError):
            self.mapper.to_external(-1) # Negative index

    def test_lookup_performance(self):

        import time
        """Verify O(1) lookup time doesn't degrade with size"""
        # Build large mapper
        for i in range(100_000):
            self.mapper.get_or_create(f"Node_{i}")

        # Time lookups at different positions
        start = time.perf_counter()
        for _ in range(10_000):
            self.mapper.to_external(50_000)  # Middle
        mid_time = time.perf_counter() - start

        start = time.perf_counter()
        for _ in range(10_000):
            self.mapper.to_external(99_999)  # End
        end_time = time.perf_counter() - start

        # O(1) means end lookup shouldn't be >10% slower
        self.assertLess(end_time / mid_time, 1.1)

    def test_hash_collisions(self):
        """Verify correct handling of strings that hash to same bucket"""
        # Strings that collide in Python's hash (requires research/generation)
        # OR test many strings and verify correctness

        colliding_strings = []
        for i in range(10_000):
            s = f"collision_test_{i:05d}"
            colliding_strings.append(s)

        # Map them all
        ids = [self.mapper.get_or_create(s) for s in colliding_strings]

        # Verify all unique
        self.assertEqual(len(set(ids)), len(colliding_strings))

        # Verify reverse lookup correctness
        for s, id_val in zip(colliding_strings, ids):
            self.assertEqual(self.mapper.to_external(id_val), s)

    def test_immutability_after_export(self):
        """Verify internal state is protected"""
        id_a = self.mapper.get_or_create("A")

        # Try to access internals (should fail or be ineffective)
        try:
            # If your implementation exposes _ext_to_int:
            self.mapper._ext_to_int["C"] = 999
            self.mapper._int_to_str[0] = "AA"
            self.mapper._int_to_str[1] = "N"
            # If this doesn't raise an error, verify it didn't affect state
            self.assertEqual(self.mapper.to_external(id_a), "A")
            self.assertFalse(self.mapper.contains_internal(1))
            self.assertFalse(self.mapper.contains_external("C"))
        except AttributeError:
            pass  # Good - internals properly hidden

        self.assertEqual(self.mapper.size(), 1)

    def test_membership_checks(self):
        """Test contains methods"""
        id_a = self.mapper.get_or_create("A")

        self.assertTrue(self.mapper.contains_external("A"))
        self.assertFalse(self.mapper.contains_external("Z"))

        self.assertTrue(self.mapper.contains_internal(id_a))
        self.assertFalse(self.mapper.contains_internal(9999))

if __name__ == "__main__":
    unittest.main()
