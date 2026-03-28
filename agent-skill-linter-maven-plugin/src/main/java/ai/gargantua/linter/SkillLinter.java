package ai.gargantua.linter;

import ai.gargantua.linter.rules.*;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core linting engine that scans skill directories for SKILL.md files
 * and applies configured lint rules.
 */
public class SkillLinter {

    private static final Map<String, LintRule> ALL_RULES = new LinkedHashMap<>();

    static {
        register(new NameMatchesFolderRule());
        register(new VersionSemverRule());
        register(new DescriptionLengthRule());
        register(new EvalsPresentRule());
        register(new ActiveMissingRule());
    }

    private static void register(LintRule rule) {
        ALL_RULES.put(rule.name(), rule);
    }

    private final List<LintRule> activeRules;

    /**
     * Creates a linter with only the specified rules enabled.
     * If {@code ruleNames} is null or empty, all rules are enabled.
     *
     * @param ruleNames list of rule names to enable, or null/empty for all
     */
    public SkillLinter(List<String> ruleNames) {
        if (ruleNames == null || ruleNames.isEmpty()) {
            this.activeRules = new ArrayList<>(ALL_RULES.values());
        } else {
            this.activeRules = ruleNames.stream()
                    .map(ALL_RULES::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Lint all skills found under the given directory.
     * Each immediate subdirectory containing a SKILL.md is treated as a skill.
     *
     * @param skillsDir the root directory containing skill folders
     * @return list of lint findings
     */
    public List<SkillLintResult> lint(File skillsDir) {
        List<SkillLintResult> results = new ArrayList<>();

        if (skillsDir == null || !skillsDir.isDirectory()) {
            return results;
        }

        File[] children = skillsDir.listFiles(File::isDirectory);
        if (children == null) {
            return results;
        }

        for (File skillDir : children) {
            File skillMd = new File(skillDir, "SKILL.md");
            if (!skillMd.isFile()) {
                continue;
            }

            try {
                String content = Files.readString(skillMd.toPath());
                SkillLintInput input = parseSkillMd(content, skillDir);

                for (LintRule rule : activeRules) {
                    rule.check(input).ifPresent(results::add);
                }
            } catch (IOException e) {
                results.add(new SkillLintResult(
                        skillDir.getName(),
                        "parse-error",
                        LintLevel.ERROR,
                        "Failed to read SKILL.md: " + e.getMessage(),
                        "SKILL.md",
                        0
                ));
            }
        }

        return results;
    }

    @SuppressWarnings("unchecked")
    private SkillLintInput parseSkillMd(String content, File skillDir) {
        Map<String, Object> frontmatter = new HashMap<>();
        String body = content;

        if (content.startsWith("---")) {
            int endIndex = content.indexOf("---", 3);
            if (endIndex > 0) {
                String yamlBlock = content.substring(3, endIndex).trim();
                body = content.substring(endIndex + 3).trim();

                Yaml yaml = new Yaml();
                Object parsed = yaml.load(yamlBlock);
                if (parsed instanceof Map) {
                    frontmatter = (Map<String, Object>) parsed;
                }
            }
        }

        String skillName = frontmatter.containsKey("name")
                ? frontmatter.get("name").toString()
                : null;

        return new SkillLintInput(
                skillName,
                skillDir.getName(),
                frontmatter,
                body,
                skillDir
        );
    }
}
