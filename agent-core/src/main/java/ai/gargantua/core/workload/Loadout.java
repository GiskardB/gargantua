package ai.gargantua.core.workload;

import java.util.List;

/**
 * The <em>loadout</em> of an agent: the specific subset of knowledge, memory, skills and
 * resources it is equipped with. Where {@link AgentSpec#memoryLayers()} toggles which
 * <em>layers</em> are active and {@link AgentSpec#capabilities()} declares what the agent
 * advertises, the loadout says which concrete <em>instances</em> the agent may draw on —
 * so an agent is given a curated set rather than implicit access to everything.
 *
 * <p>Rationale: an agent should be provisioned deliberately. Two agents on the same
 * runtime image, with the same layers enabled, can still carry different knowledge bases
 * and resources purely through their loadout. This is declarative data only — no code, no
 * credentials — so a bundle carrying a loadout stays signable and promotable unchanged.</p>
 *
 * <p>All four parts are optional; an empty loadout ({@link #empty()}) means "nothing
 * explicitly equipped", and the runtime falls back to whatever skills configure on their
 * own. Not every part is enforced by the runtime yet — the manifest carries it as intent;
 * see the runtime's unapplied-fields reporting.</p>
 *
 * @param knowledge     knowledge bases (vector collections/indexes) the agent may query;
 *                      the first-class, targeted-knowledge part of a loadout
 * @param memoryScopes  named memory collections/namespaces to attach, beyond the layer
 *                      toggles in {@link AgentSpec#memoryLayers()}
 * @param skills        skills to equip by name, beyond {@link AgentSpec#defaultSkill()}
 * @param resources     arbitrary named resources (files, datasets, endpoints)
 */
public record Loadout(
        List<KnowledgeRef> knowledge,
        List<String> memoryScopes,
        List<String> skills,
        List<ResourceRef> resources
) {

    private static final Loadout EMPTY = new Loadout(List.of(), List.of(), List.of(), List.of());

    public Loadout {
        knowledge = knowledge == null ? List.of() : List.copyOf(knowledge);
        memoryScopes = memoryScopes == null ? List.of() : List.copyOf(memoryScopes);
        skills = skills == null ? List.of() : List.copyOf(skills);
        resources = resources == null ? List.of() : List.copyOf(resources);

        long distinctKnowledge = knowledge.stream().map(KnowledgeRef::name).distinct().count();
        if (distinctKnowledge != knowledge.size()) {
            throw new IllegalArgumentException("Duplicate knowledge base names in loadout");
        }
        long distinctResources = resources.stream().map(ResourceRef::name).distinct().count();
        if (distinctResources != resources.size()) {
            throw new IllegalArgumentException("Duplicate resource names in loadout");
        }
    }

    /** The empty loadout — nothing explicitly equipped. */
    public static Loadout empty() {
        return EMPTY;
    }

    /** Whether nothing at all is equipped. */
    public boolean isEmpty() {
        return knowledge.isEmpty() && memoryScopes.isEmpty() && skills.isEmpty() && resources.isEmpty();
    }
}
