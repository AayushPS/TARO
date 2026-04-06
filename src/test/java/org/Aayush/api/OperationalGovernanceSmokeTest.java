package org.Aayush.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.Aayush.testsupport.AbstractTaroApiSpringTest;
import org.Aayush.testsupport.TaroApiSpringTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TaroApiSpringTest
@Tag("smoke")
@Tag("integration")
@DisplayName("Operational Governance Smoke Tests")
class OperationalGovernanceSmokeTest extends AbstractTaroApiSpringTest {

    @Test
    @DisplayName("Governance endpoint makes builder and serving rollback rules explicit")
    void testGovernanceEndpointMakesBuilderAndServingRollbackRulesExplicit() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/v1/governance"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        assertEquals("HEALTHY", json.path("currentOverallAlertStatus").asText());
        assertEquals("topo-api", json.path("activeTopologyVersion").asText());
        assertEquals("VALIDATE_ONLY_THEN_ATOMIC_RELOAD", json.path("builderGovernance").path("rolloutSequence").asText());
        assertEquals(
                "keep_previous_topology_snapshot_active_and_block_candidate_publication",
                json.path("builderGovernance").path("rollbackAction").asText()
        );
        assertTrue(json.path("builderGovernance").path("rollbackTriggers").toString().contains("reload_degraded"));
        assertTrue(json.path("builderGovernance").path("rollbackTriggers").toString().contains("parity_drift"));
        assertEquals("CANARY_THEN_FULL_ROLLOUT", json.path("servingGovernance").path("rolloutSequence").asText());
        assertEquals(
                "rollback_serving_artifact_and_keep_topology_bound_result_compatibility_checks_enabled",
                json.path("servingGovernance").path("rollbackAction").asText()
        );
        assertTrue(json.path("servingGovernance").path("rollbackTriggers").toString().contains("route_latency_regression"));
        assertTrue(json.path("servingGovernance").path("rollbackTriggers").toString().contains("matrix_latency_regression"));
        assertTrue(json.path("servingGovernance").path("rollbackTriggers").toString().contains("retained_result_memory_pressure"));
        assertTrue(json.path("servingGovernance").path("rollbackTriggers").toString().contains("contract_test_failure"));
    }

    @Test
    @DisplayName("Health endpoint reflects operational alert summary")
    void testHealthEndpointReflectsOperationalAlertSummary() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        assertEquals("UP", json.path("status").asText());
        assertEquals("topo-api", json.path("activeTopologyVersion").asText());
        assertEquals("quarantine-topo-api:0", json.path("quarantineSnapshotId").asText());
        assertEquals("HEALTHY", json.path("reloadHealth").asText());
        assertEquals("HEALTHY", json.path("alertStatus").asText());
    }
}
