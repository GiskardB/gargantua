package ai.gargantua.trainer;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Trains the offline skill-router classifier (Tribuo logistic regression over MiniLM
 * embeddings) from {@code examples}/{@code metadata.routing-hints} in each skill's
 * {@code SKILL.md}, and writes a protobuf-serialized model as a classpath resource.
 *
 * <p>Consume the result with {@code agent.routing.classifier.engine: tribuo} and
 * {@code agent.routing.classifier.tribuo.model-path} pointing at the output — see
 * {@code docs/architecture/offline-skill-classifier-proposal.md}.</p>
 */
@Mojo(
        name = "train-router",
        defaultPhase = LifecyclePhase.PROCESS_RESOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE
)
public class TrainRouterMojo extends AbstractMojo {

    /** Directory containing skill subdirectories, each with a SKILL.md. */
    @Parameter(property = "trainRouter.skillsDirectory",
               defaultValue = "${project.basedir}/src/main/resources/skills")
    private File skillsDirectory;

    /** Where to write the trained, protobuf-serialized Tribuo model. */
    @Parameter(property = "trainRouter.outputPath",
               defaultValue = "${project.build.outputDirectory}/models/skill-classifier.tribuo")
    private File outputPath;

    /** Minimum training phrases (examples + routing-hints + description) required per skill. */
    @Parameter(property = "trainRouter.minExamplesPerSkill", defaultValue = "3")
    private int minExamplesPerSkill;

    @Override
    public void execute() throws MojoFailureException, MojoExecutionException {
        if (!skillsDirectory.isDirectory()) {
            getLog().info("No skills directory at " + skillsDirectory + " — skipping train-router");
            return;
        }

        Map<String, List<String>> bySkill;
        try {
            bySkill = new SkillDatasetReader().readGrouped(skillsDirectory);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read SKILL.md files under " + skillsDirectory, e);
        }

        if (bySkill.isEmpty()) {
            getLog().info("No active skills with training phrases found under " + skillsDirectory + " — skipping train-router");
            return;
        }

        for (var entry : bySkill.entrySet()) {
            if (entry.getValue().size() < minExamplesPerSkill) {
                throw new MojoFailureException("Skill '" + entry.getKey() + "' has only " + entry.getValue().size()
                        + " training phrase(s) (examples + metadata.routing-hints + description); need at least "
                        + minExamplesPerSkill + ". Add more `examples`/`metadata.routing-hints` to its SKILL.md.");
            }
        }

        List<TrainingExample> dataset = new ArrayList<>();
        bySkill.forEach((skillName, phrases) ->
                phrases.forEach(phrase -> dataset.add(new TrainingExample(skillName, phrase))));

        getLog().info("Training skill router classifier on " + dataset.size() + " phrase(s) across "
                + bySkill.size() + " skill(s): " + String.join(", ", bySkill.keySet()));

        RouterTrainer trainer = new RouterTrainer();
        var model = trainer.train(dataset);

        try {
            trainer.exportModel(model, outputPath.toPath());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write trained model to " + outputPath, e);
        }

        getLog().info("Wrote trained skill router model to " + outputPath);
    }
}
