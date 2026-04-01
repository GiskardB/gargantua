/**
 * FitCoach AI — Eval Framework Runner
 *
 * Runs all golden dataset eval cases against the running agent,
 * scores output quality, and generates a Markdown report.
 *
 * Usage: node run-evals.mjs [--base-url http://localhost:8080]
 */

import { readFileSync, writeFileSync, readdirSync, existsSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const SKILLS_DIR = join(__dirname, '..', 'src', 'main', 'resources', 'skills');

const BASE = process.argv.includes('--base-url')
    ? process.argv[process.argv.indexOf('--base-url') + 1]
    : 'http://localhost:8080';

const TIMEOUT_MS = 120_000;

// ── Load all eval datasets ──────────────────────────────────────

function loadEvalDatasets() {
    const datasets = [];
    for (const skillDir of readdirSync(SKILLS_DIR)) {
        const evalsPath = join(SKILLS_DIR, skillDir, 'evals', 'evals.json');
        if (existsSync(evalsPath)) {
            const cases = JSON.parse(readFileSync(evalsPath, 'utf-8'));
            datasets.push({ skill: skillDir, cases });
        }
    }
    return datasets;
}

// ── Run a single eval case ──────────────────────────────────────

async function runEvalCase(skillName, evalCase) {
    const start = Date.now();
    try {
        const res = await fetch(`${BASE}/api/agent/chat`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-User-Id': 'eval-runner',
                'X-Session-Id': `eval-${skillName}-${evalCase.name}-${Date.now()}`,
                'X-Force-Skill': skillName,
            },
            body: JSON.stringify({ message: evalCase.input }),
            signal: AbortSignal.timeout(TIMEOUT_MS),
        });

        const data = await res.json();
        const durationMs = Date.now() - start;
        const text = (data.text || '').toLowerCase();

        // Check expected output contains
        const expected = evalCase.expectedBehaviors || evalCase.expectedOutputContains || [];
        const found = expected.filter(term => text.includes(term.toLowerCase()));
        const missing = expected.filter(term => !text.includes(term.toLowerCase()));
        const contentScore = expected.length > 0 ? found.length / expected.length : 1.0;

        // Check skill routing
        const skillMatch = data.skillUsed === skillName;

        // Overall score
        const score = skillMatch ? contentScore : 0;
        const verdict = score >= 0.66 ? 'PASS' : score >= 0.33 ? 'PARTIAL' : 'FAIL';

        return {
            name: evalCase.id || evalCase.name,
            skill: skillName,
            input: evalCase.input,
            verdict,
            score: Math.round(score * 100) / 100,
            skillMatch,
            actualSkill: data.skillUsed,
            routingMethod: data.routingMethod,
            expectedTerms: expected,
            foundTerms: found,
            missingTerms: missing,
            tokens: data.totalTokens || 0,
            durationMs,
            responseLength: (data.text || '').length,
            responsePreview: (data.text || '').substring(0, 150) + '...',
        };
    } catch (err) {
        return {
            name: evalCase.name,
            skill: skillName,
            input: evalCase.input,
            verdict: 'ERROR',
            score: 0,
            error: err.message,
            durationMs: Date.now() - start,
        };
    }
}

// ── Generate Markdown report ────────────────────────────────────

function generateReport(allResults, startTime) {
    const endTime = new Date();
    const totalDurationSec = Math.round((endTime - startTime) / 1000);

    const totalCases = allResults.length;
    const passed = allResults.filter(r => r.verdict === 'PASS').length;
    const partial = allResults.filter(r => r.verdict === 'PARTIAL').length;
    const failed = allResults.filter(r => r.verdict === 'FAIL').length;
    const errors = allResults.filter(r => r.verdict === 'ERROR').length;
    const overallScore = allResults.reduce((s, r) => s + (r.score || 0), 0) / totalCases;

    const avgTokens = Math.round(allResults.reduce((s, r) => s + (r.tokens || 0), 0) / totalCases);
    const avgDuration = Math.round(allResults.reduce((s, r) => s + (r.durationMs || 0), 0) / totalCases);
    const avgResponseLen = Math.round(allResults.reduce((s, r) => s + (r.responseLength || 0), 0) / totalCases);

    // Group by skill
    const bySkill = {};
    for (const r of allResults) {
        if (!bySkill[r.skill]) bySkill[r.skill] = [];
        bySkill[r.skill].push(r);
    }

    let md = `# FitCoach AI — Eval Report

> **Generated**: ${endTime.toISOString()}
> **Target**: ${BASE}
> **Duration**: ${totalDurationSec}s
> **Eval cases**: ${totalCases}

---

## Summary

| Metric | Value |
|--------|-------|
| **Overall Score** | **${(overallScore * 100).toFixed(1)}%** |
| Total cases | ${totalCases} |
| PASS | ${passed} |
| PARTIAL | ${partial} |
| FAIL | ${failed} |
| ERROR | ${errors} |
| Avg tokens/response | ${avgTokens} |
| Avg response time | ${avgDuration}ms |
| Avg response length | ${avgResponseLen} chars |

---

## Results by Skill

`;

    for (const [skill, results] of Object.entries(bySkill)) {
        const skillScore = results.reduce((s, r) => s + (r.score || 0), 0) / results.length;
        const skillPassed = results.filter(r => r.verdict === 'PASS').length;

        md += `### ${skill} (${skillPassed}/${results.length} PASS, score: ${(skillScore * 100).toFixed(1)}%)

| Case | Verdict | Score | Found | Missing | Tokens | Time |
|------|---------|-------|-------|---------|--------|------|
`;

        for (const r of results) {
            const icon = r.verdict === 'PASS' ? 'PASS' : r.verdict === 'PARTIAL' ? 'PARTIAL' : r.verdict === 'ERROR' ? 'ERROR' : 'FAIL';
            md += `| ${r.name} | **${icon}** | ${(r.score * 100).toFixed(0)}% | ${(r.foundTerms || []).join(', ') || '-'} | ${(r.missingTerms || []).join(', ') || '-'} | ${r.tokens} | ${r.durationMs}ms |\n`;
        }

        md += '\n';
    }

    // Detailed results for non-PASS cases
    const nonPass = allResults.filter(r => r.verdict !== 'PASS');
    if (nonPass.length > 0) {
        md += `---

## Cases Requiring Attention

`;
        for (const r of nonPass) {
            md += `### ${r.skill} / ${r.name} (${r.verdict}, ${(r.score * 100).toFixed(0)}%)

- **Input**: "${r.input}"
- **Missing terms**: ${(r.missingTerms || []).join(', ') || 'none'}
- **Found terms**: ${(r.foundTerms || []).join(', ') || 'none'}
- **Response preview**: "${r.responsePreview || r.error || 'N/A'}"

`;
        }
    }

    md += `---

## Methodology

Each eval case is executed against the running FitCoach AI instance:

1. The input message is sent to \`POST /api/agent/chat\` with \`X-Force-Skill\` header to isolate skill behavior
2. The response text is checked for presence of expected terms (case-insensitive)
3. **Score** = (found terms / expected terms). If skill routing doesn't match, score = 0
4. **Verdict**: PASS (>= 66%), PARTIAL (33-65%), FAIL (< 33%), ERROR (request failed)

### Scoring criteria

- Expected terms are intentionally **broad and flexible** (e.g., "protein" not "exactly 25g of protein")
- The eval tests **domain knowledge and relevance**, not exact wording
- A PARTIAL result means the response is relevant but incomplete
- A FAIL result means the response missed the topic entirely

### Golden datasets

| Skill | Cases | Location |
|-------|-------|----------|
| workout-skill | 10 | \`skills/workout-skill/evals/evals.json\` |
| nutrition-skill | 10 | \`skills/nutrition-skill/evals/evals.json\` |
| health-skill | 5 | \`skills/health-skill/evals/evals.json\` |
| news-skill | 3 | \`skills/news-skill/evals/evals.json\` |
| default-skill | 3 | \`skills/default-skill/evals/evals.json\` |
`;

    return md;
}

// ── Main ────────────────────────────────────────────────────────

async function main() {
    const startTime = new Date();
    console.log(`\n  FitCoach AI — Eval Framework`);
    console.log(`  Target: ${BASE}`);
    console.log(`  Time: ${startTime.toISOString()}\n`);

    // Verify app is up
    try {
        await fetch(`${BASE}/actuator/health`, { signal: AbortSignal.timeout(5000) });
    } catch {
        console.error('  ERROR: App not reachable at ' + BASE);
        process.exit(1);
    }

    const datasets = loadEvalDatasets();
    const totalCases = datasets.reduce((s, d) => s + d.cases.length, 0);
    console.log(`  Loaded ${datasets.length} skills, ${totalCases} eval cases\n`);

    const allResults = [];

    for (const dataset of datasets) {
        console.log(`  --- ${dataset.skill} (${dataset.cases.length} cases) ---`);
        for (const evalCase of dataset.cases) {
            const result = await runEvalCase(dataset.skill, evalCase);
            allResults.push(result);

            const icon = result.verdict === 'PASS' ? '\x1b[32mPASS\x1b[0m'
                       : result.verdict === 'PARTIAL' ? '\x1b[33mPART\x1b[0m'
                       : '\x1b[31mFAIL\x1b[0m';
            console.log(`  ${icon}  ${result.name} (${result.score * 100}%, ${result.durationMs}ms)`);
        }
        console.log('');
    }

    // Generate report
    const report = generateReport(allResults, startTime);
    const reportPath = join(__dirname, '..', 'EVAL-REPORT.md');
    writeFileSync(reportPath, report, 'utf-8');

    // Summary
    const passed = allResults.filter(r => r.verdict === 'PASS').length;
    const overallScore = allResults.reduce((s, r) => s + (r.score || 0), 0) / allResults.length;

    console.log('  ════════════════════════════════════════');
    console.log(`  Results: ${passed}/${totalCases} PASS, overall score: ${(overallScore * 100).toFixed(1)}%`);
    console.log(`  Report saved to: ${reportPath}`);
    console.log('  ════════════════════════════════════════\n');
}

main().catch(err => {
    console.error('Fatal error:', err.message);
    process.exit(1);
});
