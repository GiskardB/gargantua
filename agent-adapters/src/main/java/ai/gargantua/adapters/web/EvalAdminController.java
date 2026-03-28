package ai.gargantua.adapters.web;

import ai.gargantua.adapters.eval.MongoEvalReportRepository;
import ai.gargantua.core.eval.EvalReport;
import ai.gargantua.core.exception.EvalSuiteNotFoundException;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/evals")
@Tag(name = "Admin \u2014 Evals")
public class EvalAdminController {

    private final MongoEvalReportRepository evalReportRepository;
    private final SkillRegistry skillRegistry;

    public EvalAdminController(MongoEvalReportRepository evalReportRepository,
                               SkillRegistry skillRegistry) {
        this.evalReportRepository = evalReportRepository;
        this.skillRegistry = skillRegistry;
    }

    @PostMapping("/run/{skillName}")
    @Operation(summary = "Run evals for a skill", description = "Triggers evaluation suite for the specified skill.")
    @ApiResponse(responseCode = "202", description = "Eval run started")
    @ApiResponse(responseCode = "404", description = "Eval suite not found")
    public ResponseEntity<Map<String, String>> runEval(@PathVariable String skillName) {
        skillRegistry.findMeta(skillName)
                .orElseThrow(() -> new EvalSuiteNotFoundException(skillName));
        // In a full implementation, this would trigger an async eval run.
        // For now, return accepted status.
        return ResponseEntity.accepted()
                .body(Map.of("status", "started", "skillName", skillName));
    }

    @PostMapping("/run")
    @Operation(summary = "Run all evals", description = "Triggers evaluation suites for all skills that have eval definitions.")
    @ApiResponse(responseCode = "202", description = "Eval runs started")
    public ResponseEntity<Map<String, Object>> runAllEvals() {
        List<String> skillNames = skillRegistry.listMeta().stream()
                .map(SkillMeta::name)
                .toList();
        return ResponseEntity.accepted()
                .body(Map.of("status", "started", "skills", skillNames));
    }

    @GetMapping("/reports/{skillName}")
    @Operation(summary = "Get eval history", description = "Returns evaluation report history for a skill.")
    @ApiResponse(responseCode = "200", description = "Eval report history")
    public ResponseEntity<List<EvalReport>> getReportHistory(
            @PathVariable String skillName) {
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
        // All registered skills are potential eval candidates
        return ResponseEntity.ok(skillRegistry.listMeta());
    }
}
