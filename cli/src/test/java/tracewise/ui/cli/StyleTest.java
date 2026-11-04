package tracewise.ui.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tracewise.agent.TraceStep;
import tracewise.agent.Verdict;
import tracewise.verification.ClaimCheck;
import tracewise.verification.VerificationReport;
import tracewise.verification.VerificationStatus;

/** The CLI's text layout (docs/stage2/ui-design.md): every state as a word, numbers readable, prose wrapped. */
class StyleTest {

    static final Style PLAIN = new Style(false, 0);
    static final Style COLOR = new Style(true, 40);

    @Test
    void amountsAndTimesReadAsInTheGui() {
        assertEquals("48,000.00", Style.money(new BigDecimal("48000")));
        assertEquals("37,065.53", Style.money(new BigDecimal("37065.53")));
        assertEquals("", Style.money(null));
        assertEquals("2026-09-02 09:00", Style.time(Instant.parse("2026-09-02T09:00:59Z")));
        assertEquals("", Style.time(null));
        assertEquals("12m", Style.age(Duration.ofMinutes(12)));
        assertEquals("3h", Style.age(Duration.ofMinutes(200)));
        assertEquals("2d", Style.age(Duration.ofHours(50)));
    }

    @Test
    void rulesAndRiskReadAsWords() {
        assertEquals("R3 Structuring", Style.rule("R3"));
        assertEquals("R9", Style.rule("R9"));
        assertEquals("HIGH 65", PLAIN.risk(65));
        assertEquals("MED 46", PLAIN.risk(46));
        assertEquals("LOW 43", PLAIN.risk(43));
    }

    @Test
    void theClaimCheckReadsAsTheGuiBadge() {
        var ok = new VerificationReport(VerificationStatus.VERIFIED,
                List.of(new ClaimCheck("a", true, ""), new ClaimCheck("b", true, "")));
        var bad = new VerificationReport(VerificationStatus.PARTIALLY_VERIFIED,
                List.of(new ClaimCheck("a", true, ""), new ClaimCheck("b", false, "no such transaction")));
        assertEquals("VER 2/2", PLAIN.check(ok));
        assertEquals("UNVER 1/2", PLAIN.check(bad));
        assertEquals("UNVER 0/0", PLAIN.check(new VerificationReport(VerificationStatus.UNSUPPORTED, List.of())));
    }

    @Test
    void colourOnlyWhenAskedAndAlwaysWithTheWord() {
        assertEquals("ESCALATE", PLAIN.verdict(Verdict.ESCALATE));
        assertEquals("Verdict", PLAIN.label("Verdict"));
        String escalate = COLOR.verdict(Verdict.ESCALATE);
        assertTrue(escalate.startsWith("\033[") && escalate.endsWith("\033[0m") && escalate.contains("ESCALATE"), escalate);
        assertEquals("ESCALATE", Style.visible(escalate));
    }

    @Test
    void proseWrapsToTheWidthWithAnIndent() {
        String text = "C1037 received four cash deposits on consecutive days, each below the threshold.";
        String wrapped = COLOR.wrap(text, 2);
        for (String line : wrapped.split("\n")) {
            assertTrue(line.startsWith("  ") && line.length() <= 40, "[" + line + "]");
        }
        assertEquals(text, wrapped.replaceAll("\\s+", " ").strip());
        assertEquals("  " + text, PLAIN.wrap(text, 2), "width 0, not a terminal: one line, so it can be searched");
        assertEquals("  a\n  b", PLAIN.wrap("a\nb", 2), "the model's own line breaks are kept");
    }

    @Test
    void tablesAlignOnWhatIsVisibleNotOnColourCodes() {
        String table = COLOR.table(List.of("ID", "RISK", "TOTAL CAD"), "llr", List.of(
                List.of("ALR-00002", COLOR.risk(65), "48,000.00"),
                List.of("ALR-00003", COLOR.risk(43), "2,208.90")));
        List<String> lines = table.lines().map(Style::visible).toList();
        assertEquals("  ID         RISK     TOTAL CAD", lines.get(0));
        assertEquals("  ALR-00002  HIGH 65  48,000.00", lines.get(1));
        assertEquals("  ALR-00003  LOW 43    2,208.90", lines.get(2), "numbers right-aligned");
        assertEquals("   N  ID\n   1  a\n  10  bb", PLAIN.table(List.of("N", "ID"), "rl",
                List.of(List.of("1", "a"), List.of("10", "bb"))), "no trailing spaces");
    }

    static TraceStep step(String tool, String args, String result, boolean error) {
        return new TraceStep("ALR-00001", 2, tool, args, result, error, 1167);
    }

    @Test
    void anAgentStepReadsAsWhatItDidNotAsJson() {
        assertEquals("account history  C1037", PLAIN.stepText(step("account_history", "{ \"account_id\" : \"C1037\" }", "{}", false)));
        assertEquals("totals by week  C1037",
                PLAIN.stepText(step("window_aggregates", "{\"account_id\":\"C1037\",\"window\":\"week\"}", "{}", false)));
        assertEquals("indicator search  \"repeated cash deposits\"",
                PLAIN.stepText(step("search_indicators", "{\"query\":\"repeated cash deposits\",\"max_results\":8}", "[]", false)));
        assertEquals("> set priority  ALR-00001 to 1, done", PLAIN.stepText(step("set_priority",
                "{\"alert_id\":\"ALR-00001\",\"priority\":1,\"justification\":\"300 words\"}", "Done: Set alert", false)));
        assertEquals("> propose escalation  CAS-00001, waits for a person", PLAIN.stepText(step("propose_escalation",
                "{\"case_id\":\"CAS-00001\",\"justification\":\"300 words\"}", "Proposed for human approval: Escalate", false)));
        assertEquals("> propose close  CAS-00009, refused: Not done: case is closed", PLAIN.stepText(step("propose_close",
                "{\"case_id\":\"CAS-00009\",\"justification\":\"x\"}", "Not done: case is closed", true)));
        assertEquals("linked alerts  C1037, error: unknown account",
                PLAIN.stepText(step("linked_alerts", "{\"account_id\":\"C1037\"}", "unknown account", true)));
        assertEquals("some new tool  x=1", PLAIN.stepText(step("some_new_tool", "{\"x\":1}", "", false)), "unknown tools still read");
        assertEquals("account history  not json", PLAIN.stepText(step("account_history", "not json", "", false)));
    }

    @Test
    void aStepLineFitsTheTerminal() {
        String line = Style.visible(COLOR.step(step("similar_cases",
                "{\"description\":\"repeated cash deposits just under the 10,000 CAD reporting threshold on consecutive days\"}", "[]", false)));
        assertTrue(line.length() <= 40, "[" + line + "]");
        assertTrue(line.endsWith("1.2 s"), line);
        assertTrue(line.startsWith("   2  similar cases"), line);
    }
}
