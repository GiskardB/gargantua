package ai.gargantua.shell.renderer;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders data as ASCII tables in the terminal. Auto-calculates column widths
 * based on content and adds separator lines between header and data rows.
 */
@Component
public class TableRenderer {

    public String renderTable(List<String> headers, List<List<String>> rows) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }

        int columnCount = headers.size();
        int[] widths = new int[columnCount];

        // Calculate column widths from headers
        for (int i = 0; i < columnCount; i++) {
            widths[i] = headers.get(i).length();
        }

        // Calculate column widths from rows
        for (List<String> row : rows) {
            for (int i = 0; i < Math.min(columnCount, row.size()); i++) {
                String cell = row.get(i) != null ? row.get(i) : "";
                widths[i] = Math.max(widths[i], cell.length());
            }
        }

        StringBuilder sb = new StringBuilder();

        // Build separator line
        String separator = buildSeparator(widths);

        // Header
        sb.append(separator).append('\n');
        sb.append(buildRow(headers, widths)).append('\n');
        sb.append(separator).append('\n');

        // Rows
        for (List<String> row : rows) {
            List<String> paddedRow = new ArrayList<>(row);
            while (paddedRow.size() < columnCount) {
                paddedRow.add("");
            }
            sb.append(buildRow(paddedRow, widths)).append('\n');
        }

        // Footer
        sb.append(separator).append('\n');

        return sb.toString();
    }

    private String buildSeparator(int[] widths) {
        StringBuilder sb = new StringBuilder("+");
        for (int width : widths) {
            sb.append("-".repeat(width + 2)).append("+");
        }
        return sb.toString();
    }

    private String buildRow(List<String> cells, int[] widths) {
        StringBuilder sb = new StringBuilder("|");
        for (int i = 0; i < widths.length; i++) {
            String cell = i < cells.size() && cells.get(i) != null ? cells.get(i) : "";
            sb.append(" ").append(padRight(cell, widths[i])).append(" |");
        }
        return sb.toString();
    }

    private String padRight(String text, int width) {
        if (text.length() >= width) {
            return text;
        }
        return text + " ".repeat(width - text.length());
    }
}
