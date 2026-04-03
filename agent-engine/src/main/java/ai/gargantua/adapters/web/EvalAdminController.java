package ai.gargantua.adapters.web;

import ai.gargantua.adapters.eval.MongoEvalReportRepository;
import ai.gargantua.autoconfigure.EvalRunner;
import ai.gargantua.core.eval.EvalComparison;
import ai.gargantua.core.eval.EvalReport;
import ai.gargantua.core.exception.EvalSuiteNotFoundException;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Admin REST endpoint for running and reviewing skill evaluation suites.
 * Triggers eval runs, saves reports to MongoDB, retrieves historical reports,
 * and compares with previous runs for regression detection.
 */
@RestController
@RequestMapping("/api/admin/evals")
@ConditionalOnBean(MongoEvalReportRepository.class)
@Tag(name = "Admin \u2014 Evals")
public class EvalAdminController {

    private static final Logger log = LoggerFactory.getLogger(EvalAdminController.class);

    private final MongoEvalReportRepository evalReportRepository;
    private final SkillRegistry skillRegistry;
    private final EvalRunner evalRunner;
    private final double failThreshold;

    public EvalAdminController(MongoEvalReportRepository evalReportRepository,
                               SkillRegistry skillRegistry,
                               EvalRunner evalRunner,
                               double failThreshold) {
        this.evalReportRepository = evalReportRepository;
        this.skillRegistry = skillRegistry;
        this.evalRunner = evalRunner;
        this.failThreshold = failThreshold;
    }

    @PostMapping("/run/{skillName}")
    @Operation(summary = "Run evals for a skill",
               description = "Executes the eval suite for the specified skill, saves the report to MongoDB, and returns it. Returns 200 if score >= threshold, 422 if below.")
    @ApiResponse(responseCode = "200", description = "Eval passed — score above threshold")
    @ApiResponse(responseCode = "422", description = "Eval failed — score below threshold (quality regression)")
    @ApiResponse(responseCode = "404", description = "Eval suite not found for skill")
    public ResponseEntity<EvalReport> runEval(@PathVariable String skillName) {
        log.info("[Eval] Running eval suite for skill '{}'", skillName);

        skillRegistry.findMeta(skillName)
                .orElseThrow(() -> new EvalSuiteNotFoundException(skillName));

        // Run the eval suite
        EvalReport report = evalRunner.runSuite(skillName);

        // Compare with previous run
        EvalReport reportWithComparison = addComparison(report);

        // Save to MongoDB
        evalReportRepository.save(reportWithComparison);

        log.info("[Eval] Completed skill='{}': score={}, passed={}, failed={}, partial={}, threshold={}",
                skillName, "%.2f".formatted(reportWithComparison.overallScore()),
                reportWithComparison.passed(), reportWithComparison.failed(),
                reportWithComparison.partial(), failThreshold);

        // Return 200 if above threshold, 422 if below (for CI/CD integration)
        if (reportWithComparison.overallScore() < failThreshold) {
            return ResponseEntity.unprocessableEntity().body(reportWithComparison);
        }
        return ResponseEntity.ok(reportWithComparison);
    }

    @PostMapping("/run")
    @Operation(summary = "Run all evals",
               description = "Runs eval suites for all skills that have eval definitions. Returns aggregated results.")
    @ApiResponse(responseCode = "200", description = "All evals passed")
    @ApiResponse(responseCode = "422", description = "One or more evals failed")
    public ResponseEntity<Map<String, Object>> runAllEvals() {
        log.info("[Eval] Running eval suites for ALL skills");

        List<EvalReport> reports = new ArrayList<>();
        List<String> failedSkills = new ArrayList<>();

        for (SkillMeta skill : skillRegistry.listMeta()) {
            try {
                EvalReport report = evalRunner.runSuite(skill.name());
                EvalReport reportWithComparison = addComparison(report);
                evalReportRepository.save(reportWithComparison);
                reports.add(reportWithComparison);

                if (reportWithComparison.overallScore() < failThreshold) {
                    failedSkills.add(skill.name());
                }

                log.info("[Eval] Skill '{}': score={}, verdict={}",
                        skill.name(), "%.2f".formatted(reportWithComparison.overallScore()),
                        reportWithComparison.overallScore() >= failThreshold ? "PASS" : "FAIL");
            } catch (EvalSuiteNotFoundException e) {
                log.debug("[Eval] No eval suite for skill '{}', skipping", skill.name());
            } catch (Exception e) {
                log.warn("[Eval] Error running eval for skill '{}': {}", skill.name(), e.getMessage());
            }
        }

        double overallScore = reports.isEmpty() ? 0.0 :
                reports.stream().mapToDouble(EvalReport::overallScore).average().orElse(0.0);

        var result = Map.of(
                "status", failedSkills.isEmpty() ? "passed" : "failed",
                "skillsEvaluated", reports.size(),
                "overallScore", Math.round(overallScore * 100.0) / 100.0,
                "failedSkills", failedSkills,
                "reports", reports
        );

        if (!failedSkills.isEmpty()) {
            return ResponseEntity.unprocessableEntity().body(result);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/reports/{skillName}")
    @Operation(summary = "Get eval history", description = "Returns evaluation report history for a skill.")
    @ApiResponse(responseCode = "200", description = "Eval report history")
    public ResponseEntity<List<EvalReport>> getReportHistory(@PathVariable String skillName) {
        List<EvalReport> reports = evalReportRepository.findHistory(skillName, 20);
        return ResponseEntity.ok(reports);
    }

    @GetMapping("/reports/{skillName}/latest")
    @Operation(summary = "Get latest eval report", description = "Returns the most recent evaluation report for a skill.")
    @ApiResponse(responseCode = "200", description = "Latest eval report")
    @ApiResponse(responseCode = "404", description = "No eval report found")
    public ResponseEntity<EvalReport> getLatestReport(@PathVariable String skillName) {
        EvalReport report = evalReportRepository.findLatest(skillName)
                .orElseThrow(() -> new EvalSuiteNotFoundException(skillName));
        return ResponseEntity.ok(report);
    }

    @GetMapping("/skills")
    @Operation(summary = "List skills with evals", description = "Returns list of skills that have evaluation definitions.")
    @ApiResponse(responseCode = "200", description = "Skills with evals")
    public ResponseEntity<List<SkillMeta>> listSkillsWithEvals() {
        return ResponseEntity.ok(skillRegistry.listMeta());
    }

    /**
     * Add comparison with the previous report for the same skill.
     */
    private EvalReport addComparison(EvalReport report) {
        var previous = evalReportRepository.findLatest(report.skillName());
        if (previous.isEmpty()) {
            return report; // No previous run, comparison stays null
        }
        var prev = previous.get();
        var comparison = new EvalComparison(
                prev.overallScore(),
                report.overallScore() - prev.overallScore(),
                prev.runAt().toString()
        );
        return new EvalReport(
                report.skillName(),
                report.skillVersion(),
                report.runAt(),
                report.totalCases(),
                report.passed(),
                report.failed(),
                report.partial(),
                report.overallScore(),
                report.results(),
                comparison
        );
    }
}
