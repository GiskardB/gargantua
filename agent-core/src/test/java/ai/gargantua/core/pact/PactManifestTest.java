package ai.gargantua.core.pact;

import ai.gargantua.core.capability.Capability;
import ai.gargantua.core.workload.AgentSpec;
import ai.gargantua.core.workload.WorkloadManifest;
import ai.gargantua.core.workload.WorkloadMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PactManifest")
class PactManifestTest {

    @Test
    @DisplayName("projects identity, purpose, capabilities (id+description only), cognition, contract and interfaces")
    void projectsFromWorkloadManifest() {
        AgentSpec spec = new AgentSpec(
                null,
                List.of(new Capability("refund-payment", "Handles a refund", "1.0.0",
                        null, null, "refund-skill", Set.of("payments"))),
                null, null, null, null, null, null, null,
                new Cognition(Set.of("text"), Set.of("reasoning"), null, null),
                new Contract(Autonomy.RECOMMENDING, Set.of("read_repository")),
                List.of(new InterfaceEndpoint("a2a", "https://example.com/a2a", "1.0")));
        WorkloadManifest manifest = WorkloadManifest.agent(
                new WorkloadMetadata("architecture-agent", "1.0.0", "Analyzes architectures",
                        "Example Corporation", null),
                spec);

        PactManifest pact = PactManifest.from(manifest);

        assertEquals(PactManifest.CURRENT_API_VERSION, pact.apiVersion());
        assertEquals("Agent", pact.kind());
        assertEquals("architecture-agent", pact.metadata().name());
        assertEquals("1.0.0", pact.metadata().version());
        assertEquals("Analyzes architectures", pact.metadata().description());

        assertEquals("Example Corporation", pact.identity().provider());
        assertEquals("Analyzes architectures", pact.purpose().description());

        assertEquals(1, pact.capabilities().size());
        assertEquals("refund-payment", pact.capabilities().get(0).id());
        assertEquals("Handles a refund", pact.capabilities().get(0).description());

        assertEquals(Set.of("text"), pact.cognition().modalities());
        assertEquals(Autonomy.RECOMMENDING, pact.contract().autonomy());
        assertEquals("a2a", pact.interfaces().get(0).protocol());
    }

    @Test
    @DisplayName("an agent with none of the PACT fields projects to empty Cognition/Contract/interfaces")
    void projectsEmptyWhenUndeclared() {
        WorkloadManifest manifest = WorkloadManifest.agent(
                new WorkloadMetadata("minimal-agent", "1.0.0"), AgentSpec.minimal());

        PactManifest pact = PactManifest.from(manifest);

        assertTrue(pact.cognition().isEmpty());
        assertTrue(pact.contract().isEmpty());
        assertTrue(pact.interfaces().isEmpty());
        assertTrue(pact.capabilities().isEmpty());
    }

    @Test
    @DisplayName("no owner or description yields no Identity/Purpose, not blank ones")
    void noOwnerOrDescriptionYieldsNullIdentityAndPurpose() {
        WorkloadManifest manifest = WorkloadManifest.agent(
                new WorkloadMetadata("minimal-agent", "1.0.0"), AgentSpec.minimal());

        PactManifest pact = PactManifest.from(manifest);

        assertNull(pact.identity());
        assertNull(pact.purpose());
    }

    @Test
    @DisplayName("a blank owner still yields no Identity")
    void blankOwnerYieldsNoIdentity() {
        WorkloadManifest manifest = WorkloadManifest.agent(
                new WorkloadMetadata("minimal-agent", "1.0.0", "", "   ", null), AgentSpec.minimal());

        PactManifest pact = PactManifest.from(manifest);

        assertNull(pact.identity());
        assertNull(pact.purpose());
    }

    @Test
    @DisplayName("toWireMap emits autonomy as {level: N}, not the enum name")
    void toWireMapEmitsAutonomyAsWireFormatLevel() {
        AgentSpec spec = new AgentSpec(
                null, List.of(), null, null, null, null, null, null, null,
                Cognition.none(),
                new Contract(Autonomy.EXECUTING, Set.of("deploy_production")),
                List.of());
        WorkloadManifest manifest = WorkloadManifest.agent(new WorkloadMetadata("a", "1.0.0"), spec);

        Map<String, Object> wire = PactManifest.from(manifest).toWireMap();

        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) wire.get("contract");
        @SuppressWarnings("unchecked")
        Map<String, Object> autonomy = (Map<String, Object>) contract.get("autonomy");
        assertEquals(3, autonomy.get("level"));
        assertEquals(List.of("deploy_production"), contract.get("permissions"));
    }

    @Test
    @DisplayName("toWireMap nests cognition.requirements under modalities.required/capabilities.required/contextWindow.minimum")
    void toWireMapNestsCognitionRequirementsCorrectly() {
        AgentSpec spec = new AgentSpec(
                null, List.of(), null, null, null, null, null, null, null,
                new Cognition(Set.of("text"), Set.of("reasoning"),
                        new CognitionModels(new ModelDescriptor("anthropic", "claude", null), null),
                        new CognitionRequirements(Set.of("text"), Set.of("reasoning"), 64000)),
                Contract.none(),
                List.of());
        WorkloadManifest manifest = WorkloadManifest.agent(new WorkloadMetadata("a", "1.0.0"), spec);

        Map<String, Object> wire = PactManifest.from(manifest).toWireMap();

        @SuppressWarnings("unchecked")
        Map<String, Object> cognition = (Map<String, Object>) wire.get("cognition");
        assertEquals(List.of("text"), cognition.get("modalities"));
        @SuppressWarnings("unchecked")
        Map<String, Object> models = (Map<String, Object>) cognition.get("models");
        @SuppressWarnings("unchecked")
        Map<String, Object> primary = (Map<String, Object>) models.get("primary");
        assertEquals("anthropic", primary.get("provider"));

        @SuppressWarnings("unchecked")
        Map<String, Object> requirements = (Map<String, Object>) cognition.get("requirements");
        @SuppressWarnings("unchecked")
        Map<String, Object> requiredModalities = (Map<String, Object>) requirements.get("modalities");
        assertEquals(List.of("text"), requiredModalities.get("required"));
        @SuppressWarnings("unchecked")
        Map<String, Object> contextWindow = (Map<String, Object>) requirements.get("contextWindow");
        assertEquals(64000, contextWindow.get("minimum"));
    }

    @Test
    @DisplayName("toWireMap omits cognition/contract/identity/purpose/interfaces entirely when undeclared")
    void toWireMapOmitsUndeclaredSections() {
        WorkloadManifest manifest = WorkloadManifest.agent(
                new WorkloadMetadata("minimal-agent", "1.0.0"), AgentSpec.minimal());

        Map<String, Object> wire = PactManifest.from(manifest).toWireMap();

        assertFalse(wire.containsKey("cognition"));
        assertFalse(wire.containsKey("contract"));
        assertFalse(wire.containsKey("interfaces"));
        assertFalse(wire.containsKey("identity"));
        assertFalse(wire.containsKey("purpose"));
        assertFalse(wire.containsKey("capabilities"));
        assertEquals(PactManifest.CURRENT_API_VERSION, wire.get("apiVersion"));
    }
}
