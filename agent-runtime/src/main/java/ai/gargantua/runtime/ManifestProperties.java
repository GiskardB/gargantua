package ai.gargantua.runtime;

import ai.gargantua.bundle.LoadedBundle;
import ai.gargantua.core.mcp.McpServerSpec;
import ai.gargantua.core.workload.AgentSpec;
import ai.gargantua.core.workload.WorkloadManifest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Translates a bundle manifest into Spring configuration properties.
 *
 * <p>This is what makes a declarative bundle actually drive the engine. Rather than
 * teaching every component to read a manifest, the manifest is projected onto the
 * {@code agent.*} properties the engine already binds — so guardrails, memory, routing
 * and MCP behave identically whether the agent was configured by a developer's
 * {@code application.yml} or by a published bundle.</p>
 *
 * <p>The resulting source is installed just below the process environment, so a bundle
 * overrides the runtime image's defaults while an operator can still override the bundle
 * in an emergency without republishing it.</p>
 */
public final class ManifestProperties {

    private ManifestProperties() {}

    /** Projects {@code bundle} onto {@code agent.*} properties. */
    public static Map<String, Object> from(LoadedBundle bundle) {
        WorkloadManifest manifest = bundle.manifest();
        AgentSpec spec = manifest.agentSpec();
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("agent.api.agent-id", manifest.metadata().name());
        properties.put("agent.api.display-name", manifest.metadata().name());
        properties.put("agent.api.version", manifest.metadata().version());
        if (!manifest.metadata().description().isBlank()) {
            properties.put("agent.api.description", manifest.metadata().description());
        }

        // Skills ship inside the bundle; point the registry at them as a filesystem resource.
        if (bundle.hasSkills()) {
            properties.put("agent.skill.path",
                    "file:" + bundle.skillsPath().toAbsolutePath().normalize());
        }
        putIfPresent(properties, "agent.routing.fallback-skill", spec.defaultSkill());

        model(properties, spec);
        mcpServers(properties, spec.mcpServers());
        guardrails(properties, spec.guardrails());
        memoryLayers(properties, spec);

        return Map.copyOf(properties);
    }

    /**
     * Manifest fields this runtime accepts but does not yet act on.
     *
     * <p>Reported explicitly and logged at startup rather than dropped, because a
     * declaration that is silently ignored is worse than one that is rejected: the
     * operator believes a constraint is in force when it is not.</p>
     */
    public static List<String> unappliedFields(LoadedBundle bundle) {
        AgentSpec spec = bundle.manifest().agentSpec();
        List<String> warnings = new java.util.ArrayList<>();

        if (!spec.allowedRoles().isEmpty()) {
            warnings.add("spec.allowedRoles is declared (" + spec.allowedRoles()
                    + ") but workload-level RBAC is not implemented; "
                    + "declare allowed-roles per skill in SKILL.md instead");
        }
        if (spec.runtime().minVersion() != null && !spec.runtime().minVersion().isBlank()) {
            warnings.add("spec.runtime.minVersion is declared ("
                    + spec.runtime().minVersion() + ") but is not verified by the runtime; "
                    + "the Deployment Manager is expected to enforce it");
        }
        if (spec.hasLoadout()) {
            var loadout = spec.loadout();
            warnings.add("spec.loadout is declared (knowledge=" + loadout.knowledge().size()
                    + ", memoryScopes=" + loadout.memoryScopes().size()
                    + ", skills=" + loadout.skills().size()
                    + ", resources=" + loadout.resources().size()
                    + ") but loadout provisioning is not implemented yet; knowledge bases are "
                    + "currently attached per skill via SKILL.md metadata.knowledge-base");
        }
        // spec.cognition/contract/interfaces are PACT Core fields (gargantua.ai/v1 composed
        // with PACT — see PACT_v0.2_Agent_Contract_Specification.md). Unlike the fields above,
        // these are not enforcement gaps: PACT §31 "Declaration vs Verification" says a PACT
        // manifest declares, it does not prove — so parsing and reporting them without runtime
        // enforcement is the correct behaviour, not a missing feature.
        if (!spec.cognition().isEmpty()) {
            warnings.add("spec.cognition is declared but is informational only by design "
                    + "(PACT Core: a declaration, not proof of capability); no runtime "
                    + "enforcement is implied or planned");
        }
        if (!spec.contract().isEmpty()) {
            warnings.add("spec.contract is declared but is informational only by design "
                    + "(PACT Core: not a security control); use spec.allowedRoles/spec.guardrails "
                    + "for anything that must actually be enforced");
        }
        if (!spec.interfaces().isEmpty()) {
            warnings.add("spec.interfaces is declared but not cross-checked against what the "
                    + "runtime actually serves (e.g. the built-in A2A endpoint at "
                    + "/.well-known/agent.json)");
        }
        return List.copyOf(warnings);
    }

    private static void model(Map<String, Object> properties, AgentSpec spec) {
        var model = spec.model();
        putIfPresent(properties, "agent.llm.primary.model", model.primary());
        putIfPresent(properties, "agent.llm.fallback.model", model.fallback());
        putIfPresent(properties, "agent.llm.routing-model.model", model.routing());
        if (model.temperature() != null) {
            properties.put("agent.llm.primary.temperature", model.temperature());
        }
        if (model.maxTokens() != null) {
            properties.put("agent.llm.primary.max-tokens", model.maxTokens());
        }
    }

    private static void mcpServers(Map<String, Object> properties, List<McpServerSpec> servers) {
        if (servers.isEmpty()) {
            return;
        }
        properties.put("agent.mcp-client.enabled", true);
        for (int i = 0; i < servers.size(); i++) {
            McpServerSpec server = servers.get(i);
            String prefix = "agent.mcp-client.servers[" + i + "]";

            properties.put(prefix + ".name", server.name());
            properties.put(prefix + ".transport", server.transport().name().toLowerCase(Locale.ROOT));
            properties.put(prefix + ".enabled", server.enabled());
            putIfPresent(properties, prefix + ".command", server.command());
            putIfPresent(properties, prefix + ".url", server.url());

            indexed(properties, prefix + ".args", server.args());
            indexed(properties, prefix + ".allowed-tools", List.copyOf(server.allowedTools()));
            server.env().forEach((key, value) -> properties.put(prefix + ".env[" + key + "]", value));

            if (server.auth() != null && !server.auth().isNone()) {
                properties.put(prefix + ".auth.type", server.auth().type());
                putIfPresent(properties, prefix + ".auth.value", server.auth().value());
                putIfPresent(properties, prefix + ".auth.header-name", server.auth().headerName());
            }
        }
    }

    /**
     * Flattens the manifest's untyped guardrail overrides onto {@code agent.guardrail.*}.
     * Keys are converted to kebab-case so that camelCase in YAML still binds.
     */
    private static void guardrails(Map<String, Object> properties, Map<String, Object> guardrails) {
        flatten("agent.guardrail", guardrails, properties);
    }

    /**
     * Projects {@code spec.memoryLayers} onto {@code agent.memory.layers} — the sole
     * input MemoryComposer reads for a live chat request (see
     * AgentProperties.Memory#getEnabledLayers). Empty (the default) is left unset,
     * which AgentProperties.Memory also defaults to empty — both ends agree that
     * means "all three layers", so there's nothing to project.
     */
    private static void memoryLayers(Map<String, Object> properties, AgentSpec spec) {
        if (spec.usesAllMemoryLayers()) {
            return;
        }
        indexed(properties, "agent.memory.layers",
                spec.memoryLayers().stream().map(Enum::name).toList());
    }

    private static void flatten(String prefix, Map<String, Object> source, Map<String, Object> target) {
        if (source == null || source.isEmpty()) {
            return;
        }
        source.forEach((key, value) -> {
            String path = prefix + "." + kebab(String.valueOf(key));
            if (value instanceof Map<?, ?> nested) {
                Map<String, Object> typed = nested.entrySet().stream().collect(Collectors.toMap(
                        entry -> String.valueOf(entry.getKey()),
                        entry -> entry.getValue() == null ? "" : entry.getValue(),
                        (a, b) -> b,
                        LinkedHashMap::new));
                flatten(path, typed, target);
            } else if (value instanceof List<?> items) {
                indexed(target, path, items.stream().map(String::valueOf).toList());
            } else if (value != null) {
                target.put(path, value);
            }
        });
    }

    private static void indexed(Map<String, Object> properties, String prefix, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            properties.put(prefix + "[" + i + "]", values.get(i));
        }
    }

    private static void putIfPresent(Map<String, Object> properties, String key, String value) {
        if (value != null && !value.isBlank()) {
            properties.put(key, value);
        }
    }

    private static String kebab(String value) {
        StringBuilder out = new StringBuilder(value.length() + 4);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    out.append('-');
                }
                out.append(Character.toLowerCase(c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
