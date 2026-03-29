import { buildRouteResultViewModel } from "@shared/api/transforms";

describe("buildRouteResultViewModel", () => {
  it("normalizes route summaries and scenario geometry", () => {
    const viewModel = buildRouteResultViewModel(
      {
        resultSetId: "route-1",
        retained: true,
        expiresAt: "2026-03-29T00:10:00Z",
        topologyVersion: { topologyVersion: "topo-api" },
        summary: {
          resultSetId: "route-1",
          scenarioBundleId: "bundle-api",
          scenarioCount: 2,
          expectedRoute: {
            route: {
              pathExternalNodeIds: ["N0", "N1"],
              pathPoints: [{ x: 10, y: 20 }, { x: 11, y: 21 }]
            },
            expectedCost: 123,
            p90Cost: 160,
            optimalityProbability: 0.61,
            dominantScenarioId: "baseline",
            explanationTags: ["baseline"]
          },
          robustRoute: {
            route: {
              pathExternalNodeIds: ["N0", "N2"],
              pathPoints: [{ x: 10, y: 20 }, { x: 13, y: 22 }]
            },
            expectedCost: 130,
            p90Cost: 180,
            optimalityProbability: 0.39,
            dominantScenarioId: "incident"
          },
          alternatives: []
        }
      },
      {
        scenarioResults: [
          {
            scenarioId: "baseline",
            label: "Baseline",
            probability: 0.61,
            route: {
              reachable: true,
              pathExternalNodeIds: ["N0", "N1"]
            },
            pathPoints: [{ x: 10, y: 20 }, { x: 11, y: 21 }]
          }
        ]
      }
    );

    expect(viewModel.resultSetId).toBe("route-1");
    expect(viewModel.expectedRoute.route.pathPoints).toHaveLength(2);
    expect(viewModel.scenarios[0].route.pathPoints).toHaveLength(2);
    expect(viewModel.topologyVersionId).toBe("topo-api");
  });
});
