package ai.gargantua.eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

/**
 * Generates a single-file HTML report with inline CSS (no external dependencies).
 */
public class HtmlReportGenerator {

    public static void generate(EvalReport report, Path outputPath) throws IOException {
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, buildHtml(report));
    }

    private static String buildHtml(EvalReport report) {
        var sb = new StringBuilder();
        sb.append("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Agent Eval Report</title>
            <style>
            * { margin: 0; padding: 0; box-sizing: border-box; }
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                   background: #f5f5f5; color: #1a1a1a; line-height: 1.6; }
            .container { max-width: 960px; margin: 0 auto; padding: 24px; }
            h1 { font-size: 24px; font-weight: 600; margin-bottom: 8px; }
            .subtitle { color: #666; font-size: 14px; margin-bottom: 24px; }
            .summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
                       gap: 16px; margin-bottom: 32px; }
            .stat { background: #fff; border-radius: 8px; padding: 16px;
                    box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
            .stat-value { font-size: 28px; font-weight: 700; }
            .stat-label { font-size: 12px; color: #888; text-transform: uppercase; letter-spacing: 0.5px; }
            .score-pass { color: #16a34a; }
            .score-fail { color: #dc2626; }
            .score-partial { color: #ca8a04; }
            .card { background: #fff; border-radius: 8px; margin-bottom: 12px;
                    box-shadow: 0 1px 3px rgba(0,0,0,0.08); overflow: hidden; }
            .card-header { padding: 14px 18px; cursor: pointer; display: flex;
                           align-items: center; justify-content: space-between;
                           border-left: 4px solid #ccc; }
            .card-header.pass { border-left-color: #16a34a; }
            .card-header.fail { border-left-color: #dc2626; }
            .card-header.partial { border-left-color: #ca8a04; }
            .card-header:hover { background: #fafafa; }
            .card-title { font-weight: 500; font-size: 14px; }
            .card-meta { font-size: 13px; color: #666; display: flex; gap: 16px; }
            .badge { display: inline-block; padding: 2px 8px; border-radius: 4px;
                     font-size: 11px; font-weight: 600; text-transform: uppercase; }
            .badge-pass { background: #dcfce7; color: #16a34a; }
            .badge-fail { background: #fee2e2; color: #dc2626; }
            .badge-partial { background: #fef9c3; color: #ca8a04; }
            .card-body { display: none; padding: 16px 18px; border-top: 1px solid #eee;
                         font-size: 13px; }
            .card-body.open { display: block; }
            .field { margin-bottom: 12px; }
            .field-label { font-weight: 600; font-size: 11px; text-transform: uppercase;
                           color: #888; margin-bottom: 4px; letter-spacing: 0.3px; }
            .field-value { background: #f8f8f8; padding: 10px 12px; border-radius: 6px;
                           white-space: pre-wrap; word-break: break-word; font-family: monospace;
                           font-size: 12px; }
            .behaviors { display: flex; flex-wrap: wrap; gap: 6px; }
            .beh { padding: 3px 8px; border-radius: 4px; font-size: 11px; }
            .beh-pass { background: #dcfce7; color: #166534; }
            .beh-fail { background: #fee2e2; color: #991b1b; }
            </style>
            </head>
            <body>
            <div class="container">
            <h1>Agent Eval Report</h1>
            """);

        sb.append("<div class=\"subtitle\">%s &mdash; %s</div>\n".formatted(
                escapeHtml(report.agentUrl()), escapeHtml(report.runAt())));

        // Summary stats
        var scoreClass = report.overallScore() >= 0.85 ? "score-pass"
                : report.overallScore() <= 0.3 ? "score-fail" : "score-partial";

        sb.append("""
            <div class="summary">
              <div class="stat"><div class="stat-value %s">%.0f%%</div><div class="stat-label">Overall Score</div></div>
              <div class="stat"><div class="stat-value">%d</div><div class="stat-label">Total Cases</div></div>
              <div class="stat"><div class="stat-value score-pass">%d</div><div class="stat-label">Passed</div></div>
              <div class="stat"><div class="stat-value score-fail">%d</div><div class="stat-label">Failed</div></div>
              <div class="stat"><div class="stat-value score-partial">%d</div><div class="stat-label">Partial</div></div>
            </div>
            """.formatted(scoreClass, report.overallScore() * 100,
                report.totalCases(), report.passed(), report.failed(), report.partial()));

        // Per-case cards
        for (var r : report.results()) {
            var verdictLower = r.verdict().toLowerCase();
            var badgeClass = "badge-" + verdictLower;
            sb.append("""
                <div class="card">
                  <div class="card-header %s" onclick="this.nextElementSibling.classList.toggle('open')">
                    <div>
                      <span class="card-title">%s</span>
                    </div>
                    <div class="card-meta">
                      <span class="badge %s">%s</span>
                      <span>%.2f</span>
                      <span>%dms</span>
                    </div>
                  </div>
                  <div class="card-body">
                    <div class="field">
                      <div class="field-label">Input</div>
                      <div class="field-value">%s</div>
                    </div>
                    <div class="field">
                      <div class="field-label">Agent Response</div>
                      <div class="field-value">%s</div>
                    </div>
                    <div class="field">
                      <div class="field-label">Reason</div>
                      <div class="field-value">%s</div>
                    </div>
                """.formatted(
                    verdictLower,
                    escapeHtml(r.caseId()),
                    badgeClass, r.verdict(),
                    r.score(), r.durationMs(),
                    escapeHtml(r.input()),
                    escapeHtml(r.agentResponse()),
                    escapeHtml(r.reason())));

            // Passed behaviors
            if (r.passedBehaviors() != null && !r.passedBehaviors().isEmpty()) {
                sb.append("<div class=\"field\"><div class=\"field-label\">Passed</div><div class=\"behaviors\">");
                for (var b : r.passedBehaviors()) {
                    sb.append("<span class=\"beh beh-pass\">%s</span>".formatted(escapeHtml(b)));
                }
                sb.append("</div></div>\n");
            }

            // Failed behaviors
            if (r.failedBehaviors() != null && !r.failedBehaviors().isEmpty()) {
                sb.append("<div class=\"field\"><div class=\"field-label\">Failed</div><div class=\"behaviors\">");
                for (var b : r.failedBehaviors()) {
                    sb.append("<span class=\"beh beh-fail\">%s</span>".formatted(escapeHtml(b)));
                }
                sb.append("</div></div>\n");
            }

            sb.append("</div></div>\n");
        }

        sb.append("""
            </div>
            </body>
            </html>
            """);

        return sb.toString();
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
