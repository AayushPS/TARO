import unittest
import flatbuffers
import numpy as np
import time

import taro.model.Model as TaroModel
import taro.model.GraphTopology as TaroTopology
import taro.model.Coordinate as TaroCoordinate
import taro.model.IdMapping as TaroIdMapping
import taro.model.Landmark as TaroLandmark
import taro.model.TurnCost as TaroTurnCost

class TestTaroCSR(unittest.TestCase):
    """Critical tests for CSR format"""

    def test_csr_graph_traversal(self):
        """Test CSR neighbor lookup - THE CRITICAL FEATURE"""
        builder = flatbuffers.Builder(1024)

        # Graph: 0->[1,2], 1->[2], 2->[3], 3->[]

        # CSR arrays
        first_edge = np.array([0, 2, 3, 4, 4], dtype=np.int32)
        edge_target = np.array([1, 2, 2, 3], dtype=np.int32)
        weights = np.array([10.0, 15.0, 5.0, 8.0], dtype=np.float32)

        # Create vectors
        first_edge_vec = builder.CreateNumpyVector(first_edge)
        edge_target_vec = builder.CreateNumpyVector(edge_target)
        weights_vec = builder.CreateNumpyVector(weights)

        # Coordinates
        TaroTopology.StartCoordinatesVector(builder, 4)
        for _ in range(4):
            TaroCoordinate.CreateCoordinate(builder, 40.0, -74.0)
        coords_vec = builder.EndVector()

        # Topology
        TaroTopology.Start(builder)
        TaroTopology.AddNodeCount(builder, 4)
        TaroTopology.AddEdgeCount(builder, 4)
        TaroTopology.AddFirstEdge(builder, first_edge_vec)
        TaroTopology.AddEdgeTarget(builder, edge_target_vec)
        TaroTopology.AddBaseWeights(builder, weights_vec)
        TaroTopology.AddCoordinates(builder, coords_vec)
        topo_off = TaroTopology.End(builder)

        # Minimal model
        TaroModel.Start(builder)
        TaroModel.AddTopology(builder, topo_off)
        root = TaroModel.End(builder)
        builder.Finish(root)

        # Read back
        buf = bytes(builder.Output())
        model = TaroModel.Model.GetRootAsModel(buf, 0)
        topo = model.Topology()

        # === TEST CSR ===

        # Node 0: should have edges [0, 1]
        node0_start = topo.FirstEdge(0)
        node0_end = topo.FirstEdge(1)
        self.assertEqual(0, node0_start)
        self.assertEqual(2, node0_end)
        self.assertEqual(2, node0_end - node0_start, "Node 0 should have 2 edges")

        # Check neighbors
        node0_neighbors = []
        for i in range(node0_start, node0_end):
            node0_neighbors.append(topo.EdgeTarget(i))
        self.assertEqual([1, 2], node0_neighbors)

        # Node 3: should have no edges
        node3_start = topo.FirstEdge(3)
        node3_end = topo.FirstEdge(4)
        self.assertEqual(0, node3_end - node3_start, "Node 3 should have 0 edges")

    def test_id_mapping_binary_search(self):
        """Test implicit internal ID mapping"""
        builder = flatbuffers.Builder(1024)

        # Sorted external IDs
        external_ids = np.array([100, 200, 300, 400, 500], dtype=np.int64)
        ext_vec = builder.CreateNumpyVector(external_ids)

        TaroIdMapping.Start(builder)
        TaroIdMapping.AddExternalIds(builder, ext_vec)
        map_off = TaroIdMapping.End(builder)

        TaroModel.Start(builder)
        TaroModel.AddIdMapping(builder, map_off)
        root = TaroModel.End(builder)
        builder.Finish(root)

        # Read back
        buf = bytes(builder.Output())
        model = TaroModel.Model.GetRootAsModel(buf, 0)
        mapping = model.IdMapping()

        # Manual binary search
        def find_internal_id(ext_id):
            left, right = 0, mapping.ExternalIdsLength() - 1
            while left <= right:
                mid = (left + right) // 2
                mid_val = mapping.ExternalIds(mid)
                if mid_val == ext_id:
                    return mid
                elif mid_val < ext_id:
                    left = mid + 1
                else:
                    right = mid - 1
            return -1

        # Test found
        self.assertEqual(2, find_internal_id(300))

        # Test not found
        self.assertEqual(-1, find_internal_id(250))

    def test_bidirectional_landmarks(self):
        """Test forward and backward landmark distances"""
        builder = flatbuffers.Builder(1024)

        forward = np.array([0.0, 10.0, 20.0, 30.0, 40.0], dtype=np.float32)
        backward = np.array([0.0, 12.0, 22.0, 32.0, 42.0], dtype=np.float32)

        fwd_vec = builder.CreateNumpyVector(forward)
        bwd_vec = builder.CreateNumpyVector(backward)

        TaroLandmark.Start(builder)
        TaroLandmark.AddNodeIdx(builder, 0)
        TaroLandmark.AddForwardDistances(builder, fwd_vec)
        TaroLandmark.AddBackwardDistances(builder, bwd_vec)
        lm_off = TaroLandmark.End(builder)

        TaroModel.StartLandmarksVector(builder, 1)
        builder.PrependUOffsetTRelative(lm_off)
        lm_vec = builder.EndVector()

        TaroModel.Start(builder)
        TaroModel.AddLandmarks(builder, lm_vec)
        root = TaroModel.End(builder)
        builder.Finish(root)

        # Read back
        buf = bytes(builder.Output())
        model = TaroModel.Model.GetRootAsModel(buf, 0)

        lm = model.Landmarks(0)

        # Both arrays exist
        self.assertEqual(5, lm.ForwardDistancesLength())
        self.assertEqual(5, lm.BackwardDistancesLength())

        # They're different
        self.assertNotEqual(lm.ForwardDistances(1), lm.BackwardDistances(1))

    def test_csr_performance_benchmark(self):
        """Benchmark CSR neighbor lookup speed"""
        builder = flatbuffers.Builder(10 * 1024 * 1024)

        node_count = 10_000
        edge_count = 50_000

        # Generate random CSR
        import random
        random.seed(42)

        first_edge = [0]
        edge_target = []
        weights = []

        current_edge = 0
        for node in range(node_count):
            degree = random.randint(0, 10)
            degree = min(degree, edge_count - current_edge)

            for _ in range(degree):
                edge_target.append(random.randint(0, node_count - 1))
                weights.append(random.random() * 100)
                current_edge += 1

            first_edge.append(current_edge)

        # Build
        first_edge_vec = builder.CreateNumpyVector(np.array(first_edge, dtype=np.int32))
        edge_target_vec = builder.CreateNumpyVector(np.array(edge_target, dtype=np.int32))
        weights_vec = builder.CreateNumpyVector(np.array(weights, dtype=np.float32))

        TaroTopology.Start(builder)
        TaroTopology.AddNodeCount(builder, node_count)
        TaroTopology.AddEdgeCount(builder, current_edge)
        TaroTopology.AddFirstEdge(builder, first_edge_vec)
        TaroTopology.AddEdgeTarget(builder, edge_target_vec)
        TaroTopology.AddBaseWeights(builder, weights_vec)
        topo_off = TaroTopology.End(builder)

        TaroModel.Start(builder)
        TaroModel.AddTopology(builder, topo_off)
        root = TaroModel.End(builder)
        builder.Finish(root)

        # Benchmark
        buf = bytes(builder.Output())
        model = TaroModel.Model.GetRootAsModel(buf, 0)
        topo = model.Topology()

        start = time.perf_counter()
        total_neighbors = 0

        for _ in range(1000):
            node_id = random.randint(0, node_count - 1)
            edge_start = topo.FirstEdge(node_id)
            edge_end = topo.FirstEdge(node_id + 1)

            for e in range(edge_start, edge_end):
                target = topo.EdgeTarget(e)
                weight = topo.BaseWeights(e)
                total_neighbors += 1

        elapsed = (time.perf_counter() - start) * 1_000_000  # Convert to microseconds
        avg_per_query = elapsed / 1000

        print(f"\nCSR Performance Benchmark:")
        print(f"  Nodes: {node_count}")
        print(f"  Edges: {current_edge}")
        print(f"  Queries: 1000")
        print(f"  Total neighbors: {total_neighbors}")
        print(f"  Avg per query: {avg_per_query:.2f} µs")

        # Should be <100µs per query
        self.assertLess(avg_per_query, 100,
                        f"CSR should be <100µs per query (was {avg_per_query:.2f}µs)")

if __name__ == '__main__':
    unittest.main()