package tracewise.ui.cli;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tracewise.agent.TraceStep;
import tracewise.agent.Verdict;
import tracewise.verification.VerificationReport;
import tracewise.verification.VerificationStatus;

/**
 * The CLI's text layout, in the GUI's terms (docs/stage2/ui-design.md): every state is a word (HIGH 65, VER 6/6,
 * ESCALATE) and colour only repeats it; amounts with thousands separators, times as the GUI shows them, prose wrapped.
 * Colours are Bloomberg's: amber labels, a white-on-blue title bar. With color off (a pipe, NO_COLOR, a test) no
 * escape code is written; with width 0 nothing is wrapped, so piped output keeps one record per line.
 */
final class Style {

    private static final String RESET = "\033[0m";
    private static final String AMBER = "1;38;5;214", BAR = "1;97;48;5;25", RED = "1;38;5;203", YELLOW = "1;38;5;228",
            GREEN = "1;38;5;77", GREY = "38;5;245", BOLD = "1";
    /** The widest a line gets on a wide terminal; longer lines are hard to read. */
    static final int MAX_WIDTH = 100;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);
    private static final Map<String, String> RULES = Map.of("R1", "Large cash", "R2", "Large international transfer",
            "R3", "Structuring", "R4", "Rapid pass-through", "R5", "Fan-in", "R6", "Fan-out");
    /** Tools that change something: their step lines are marked, like the GUI's action rows. */
    private static final Set<String> ACTIONS = Set.of("set_priority", "propose_escalation", "propose_close",
            "create_task", "link_alerts", "add_to_watchlist");
    private static final Map<String, String> TOOL_WORDS = Map.ofEntries(Map.entry("account_history", "account history"),
            Map.entry("linked_alerts", "linked alerts"), Map.entry("counterparties", "counterparties"),
            Map.entry("search_indicators", "indicator search"), Map.entry("similar_cases", "similar cases"),
            Map.entry("get_case", "read case"), Map.entry("query_alerts", "search alerts"),
            Map.entry("query_cases", "search cases"));
    /** Arguments a step line leaves out: the justification is shown with the proposal itself. */
    private static final Set<String> NOT_SHOWN = Set.of("justification", "reason", "max_results");

    final boolean color;
    /** 0: do not wrap or cut. */
    final int width;

    Style(boolean color, int width) {
        this.color = color;
        this.width = width;
    }

    // ---- colour, each paired with the word it repeats

    private String paint(String code, String text) {
        return color && !text.isEmpty() ? "\033[" + code + "m" + text + RESET : text;
    }

    String label(String text) {
        return paint(AMBER, text);
    }

    String bold(String text) {
        return paint(BOLD, text);
    }

    String dim(String text) {
        return paint(GREY, text);
    }

    String bad(String text) {
        return paint(RED, text);
    }

    String waiting(String text) {
        return paint(YELLOW, text);
    }

    String ok(String text) {
        return paint(GREEN, text);
    }

    /** The shell's title bar: white on blue across the line. */
    String bar(String left, String right) {
        int w = width == 0 ? MAX_WIDTH : width;
        String line = " " + left + " ".repeat(Math.max(2, w - left.length() - right.length() - 2)) + right + " ";
        return color ? paint(BAR, line) : left + "  " + right;
    }

    /** The GUI's bands: 60 and over HIGH, 45 and over MED. */
    String risk(int score) {
        String text = (score >= 60 ? "HIGH " : score >= 45 ? "MED " : "LOW ") + score;
        return score >= 60 ? bad(text) : score >= 45 ? waiting(text) : dim(text);
    }

    String verdict(Verdict v) {
        if (v == null) {
            return "";
        }
        return switch (v) {
            case ESCALATE -> bad(v.name());
            case INCONCLUSIVE -> waiting(v.name());
            case DISMISS -> ok(v.name());
        };
    }

    /** VER when every cited claim matched the data, otherwise UNVER (F05), with how many were confirmed. */
    String check(VerificationReport r) {
        long confirmed = r.checks().stream().filter(c -> c.confirmed()).count();
        boolean verified = r.status() == VerificationStatus.VERIFIED;
        String text = (verified ? "VER " : "UNVER ") + confirmed + "/" + r.checks().size();
        return verified ? ok(text) : bad(text);
    }

    // ---- numbers and words

    static String money(BigDecimal amount) {
        return amount == null ? "" : String.format("%,.2f", amount);
    }

    static String time(Instant t) {
        return t == null ? "" : TIME.format(t);
    }

    static String age(Duration d) {
        long minutes = d.toMinutes();
        return minutes < 1 ? "now" : minutes < 60 ? minutes + "m" : minutes < 24 * 60 ? minutes / 60 + "h"
                : minutes / (24 * 60) + "d";
    }

    static String rule(String id) {
        String name = RULES.get(id);
        return name == null ? id : id + " " + name;
    }

    /** The text without colour codes: what the reader sees, and what columns line up on. */
    static String visible(String text) {
        return text.replaceAll("\033\\[[0-9;]*m", "");
    }

    // ---- layout

    /** Prose with every line indented, wrapped to the width; the text's own line breaks are kept. */
    String wrap(String text, int indent) {
        String pad = " ".repeat(indent);
        var lines = new ArrayList<String>();
        for (String paragraph : text.strip().split("\\R")) {
            if (width == 0) {
                lines.add(pad + paragraph.strip());
                continue;
            }
            var line = new StringBuilder(pad);
            for (String word : paragraph.strip().split(" +")) {
                if (line.length() > indent && line.length() + 1 + word.length() > width) {
                    lines.add(line.toString());
                    line = new StringBuilder(pad);
                }
                line.append(line.length() > indent ? " " : "").append(word);
            }
            lines.add(line.toString());
        }
        return String.join("\n", lines);
    }

    /** "  Label     value", the label in amber; a long value wraps under itself. */
    String field(String name, String value) {
        String head = "  " + label(String.format("%-10s", name));
        String plain = visible(value);
        if (width == 0 || 12 + plain.length() <= width || !plain.equals(value)) {
            return head + value;
        }
        return head + wrap(value, 12).substring(12);
    }

    /**
     * Columns padded on what is visible, so coloured cells line up; align has one letter per column, l or r
     * (numbers go right). Headers in amber.
     */
    String table(List<String> headers, String align, List<List<String>> rows) {
        int[] widths = new int[headers.size()];
        for (int c = 0; c < widths.length; c++) {
            widths[c] = headers.get(c).length();
            for (List<String> row : rows) {
                widths[c] = Math.max(widths[c], visible(row.get(c)).length());
            }
        }
        var out = new StringBuilder();
        var all = new ArrayList<List<String>>();
        all.add(headers.stream().map(this::label).toList());
        all.addAll(rows);
        for (List<String> row : all) {
            var line = new StringBuilder("  ");
            for (int c = 0; c < widths.length; c++) {
                String cell = row.get(c);
                String pad = " ".repeat(widths[c] - visible(cell).length());
                line.append(c == 0 ? "" : "  ").append(align.charAt(c) == 'r' ? pad + cell : cell + pad);
            }
            out.append(line.toString().stripTrailing()).append('\n');
        }
        return out.toString().stripTrailing();
    }

    /** The line cut to the width, so a table of long details stays one row per line. */
    String cut(String line) {
        String plain = visible(line);
        if (width == 0 || plain.length() <= width) {
            return line;
        }
        var kept = new StringBuilder();
        int seen = 0;
        for (int i = 0; i < line.length() && seen < width - 3; i++) {
            int end = line.charAt(i) == '\033' ? line.indexOf('m', i) : -1;
            if (end > 0) { // a colour code takes no room: keep it whole
                kept.append(line, i, end + 1);
                i = end;
            } else {
                kept.append(line.charAt(i));
                seen++;
            }
        }
        return kept + "..." + (plain.equals(line) ? "" : RESET);
    }

    // ---- agent steps

    /** One agent step on one line: turn, what it did in words, how long it took; fits the terminal. */
    String step(TraceStep s) {
        String took = s.durationMs() < 1000 ? s.durationMs() + " ms" : String.format("%.1f s", s.durationMs() / 1000.0);
        String text = stepText(s);
        int room = (width == 0 ? Integer.MAX_VALUE : width) - 6 - took.length() - 2;
        if (text.length() > room) {
            text = text.substring(0, Math.max(0, room - 3)) + "...";
        }
        String pad = width == 0 ? "  " : " ".repeat(Math.max(2, room - text.length() + 2));
        boolean action = ACTIONS.contains(s.toolName());
        String shown = action ? label(text) : s.error() ? bad(text) : text;
        return "  " + dim(String.format("%2d", s.turn())) + "  " + shown + pad + dim(took);
    }

    /** What a step did in words, from the tool's name and arguments; never the raw JSON. */
    String stepText(TraceStep s) {
        String text = stepWords(s.toolName(), s.arguments());
        if (ACTIONS.contains(s.toolName())) {
            String result = s.result() == null ? "" : s.result();
            return text + (s.error() ? ", refused: " + result : result.startsWith("Proposed") ? ", waits for a person" : ", done");
        }
        return s.error() ? text + ", error: " + s.result() : text;
    }

    /** A tool call in words, as the audit trail records it: "account_history { ... }" reads "account history  C1037". */
    String auditStep(String details) {
        int space = details.indexOf(' ');
        return space < 0 ? details : stepWords(details.substring(0, space), details.substring(space + 1));
    }

    private String stepWords(String tool, String arguments) {
        JsonNode args;
        try {
            args = CliCommandBase.JSON.readTree(arguments);
        } catch (java.io.IOException e) {
            args = null;
        }
        String words = TOOL_WORDS.getOrDefault(tool, tool.replace('_', ' '));
        String detail;
        if (args == null || !args.isObject()) {
            detail = arguments.strip();
        } else if (tool.equals("window_aggregates")) {
            words = "totals by " + args.path("window").asText("day");
            detail = args.path("account_id").asText();
        } else if (tool.equals("set_priority")) {
            detail = args.path("alert_id").asText() + " to " + args.path("priority").asText();
        } else if (TOOL_WORDS.containsKey(tool) && !tool.startsWith("query_") || ACTIONS.contains(tool)) {
            var parts = new ArrayList<String>();
            args.fields().forEachRemaining(e -> {
                if (!NOT_SHOWN.contains(e.getKey())) {
                    String v = e.getValue().asText();
                    parts.add(v.contains(" ") ? "\"" + v + "\"" : v);
                }
            });
            detail = String.join(" ", parts);
        } else {
            var parts = new ArrayList<String>();
            args.fields().forEachRemaining(e -> parts.add(e.getKey() + "=" + e.getValue().asText()));
            detail = String.join(" ", parts);
        }
        if (ACTIONS.contains(tool)) {
            words = (color ? "▸ " : "> ") + words;
        }
        return words + "  " + detail;
    }
}
