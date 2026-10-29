package tracewise.ui.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.agent.AgentRunnerTest;
import tracewise.agent.AgentRunnerTest.ScriptedModel;
import tracewise.app.AppConfig;
import tracewise.app.TraceWiseBootstrap;
import tracewise.domain.ActorType;
import tracewise.llm.LLMException;
import tracewise.llm.ModelConfig;
import tracewise.tools.ScenarioGenerator;

/** F04 from the CLI with the real embedding model: indicators search and cases similar. */
class KnowledgeCliTest {

    static final String STRUCTURING = "Multiple transactions conducted below the reporting threshold within a short period.";
    static final String PASS_THROUGH = "Funds transferred in and out of an account on the same day or within a relatively "
            + "short period of time.";

    /** Narratives from the live Haiku run of 2026-10-27 (ALR-00001 and ALR-00002). */
    static final String LIVE_STRUCTURING = "C1037 received four cash deposits on four consecutive days: 8673.80 CAD (TX-00121, 2026-09"
            + "-02), 9016.77 CAD (TX-00183, 2026-09-03), 9555.41 CAD (TX-00186, 2026-09-04) and 9819.55 C"
            + "AD (TX-00251, 2026-09-05). Each is below the 10,000 CAD reporting threshold and the amount"
            + "s rise steadily, totalling 37065.53 CAD. The account has no outgoing transactions and its "
            + "only counterparty is the CASH counter. No payroll or business income explains the funds, a"
            + "nd no other alerts are linked. This pattern gives reasonable grounds to suspect structurin"
            + "g to avoid reporting. The depositor's identity and the source of the cash are not in the d"
            + "ata, so a follow-up task was opened on CAS-00001. Escalation has been proposed for analyst"
            + " approval.";
    static final String LIVE_PASS_THROUGH = "C1059 received a 48000.00 CAD wire from EXT-2224 (TX-00454) on 2026-09-16 and sent 45500.0"
            + "0 CAD by wire to EXT-2137 (TX-00496) the same day, about five hours later. About 94.8% of "
            + "the inbound funds left the account within hours. The account has no other transactions, so"
            + " there is no payroll, business income or other activity to explain the flow. Both counterp"
            + "arties are domestic. No similar precedent was found. Customer profile, source of funds and"
            + " the purpose of the transfers remain to be obtained (task created on CAS-00002). Escalatio"
            + "n is proposed for analyst approval.";

    @TempDir Path dir;
    StringWriter out;
    StringWriter err;

    int run(String... args) {
        out = new StringWriter();
        err = new StringWriter();
        var all = new ArrayList<>(List.of("--db", dir.resolve("t.db").toString()));
        all.addAll(List.of(args));
        return TraceWiseCli.commandLine(new PrintWriter(out, true), new PrintWriter(err, true))
                .execute(all.toArray(String[]::new));
    }

    /** The agent investigates the alert, proposes the action and reports the narrative. */
    void agentFinds(String alertId, String tool, String narrative) {
        agentFinds(alertId, tool, STRUCTURING, narrative);
    }

    void agentFinds(String alertId, String tool, String indicator, String narrative) {
        String caseId = "CAS-" + alertId.substring(4);
        var model = new ScriptedModel()
                .then(AgentRunnerTest.toolCall(tool, "{\"case_id\":\"" + caseId + "\",\"justification\":\"Because\"}"))
                .then(AgentRunnerTest.answer("{\"verdict\":\"ESCALATE\",\"confidence\":0.8,\"indicators\":[\""
                        + indicator + "\"],\"cited_transactions\":[],\"totals\":[],\"narrative\":\"" + narrative + "\"}"));
        try (var facade = TraceWiseBootstrap.create(config(), () -> model)) {
            facade.investigate(alertId, null);
        }
    }

    AppConfig config() {
        return new AppConfig(dir.resolve("t.db"), "agent-run", ActorType.ANALYST,
                ModelConfig.of("anthropic", "claude-haiku-5-5", false), dir.resolve("replays"), dir.resolve(".env"));
    }

    @BeforeEach
    void setUp() throws Exception {
        Path file = Files.write(dir.resolve("s.csv"), ScenarioGenerator.generate(42));
        assertEquals(0, run("import", file.toString()));
        assertEquals(0, run("monitor", "run"));
        agentFinds("ALR-00001", "propose_escalation", LIVE_STRUCTURING);
        assertEquals(0, run("approvals", "approve", "APR-00001"));
        agentFinds("ALR-00003", "set_priority", "Cash deposits just below the reporting threshold, repeated within days.");
        agentFinds("ALR-00002", "set_priority", PASS_THROUGH, LIVE_PASS_THROUGH);
    }

    @Test
    void indicatorsSearchListsIndicatorsAndPastCasesWithScores() throws Exception {
        assertEquals(0, run("indicators", "search", "--json", "deposits below the reporting threshold"));

        JsonNode results = new ObjectMapper().readTree(out.toString());
        assertEquals(STRUCTURING, results.get(0).get("text").asText());
        assertTrue(results.get(0).get("source").asText().startsWith("FINTRAC 7."));
        var sources = new ArrayList<String>();
        results.forEach(r -> sources.add(r.get("source").asText()));
        assertTrue(sources.contains("CAS-00001"), sources.toString());

        assertEquals(0, run("indicators", "search", "deposits", "below", "the", "reporting", "threshold"));
        assertTrue(out.toString().contains(STRUCTURING), out.toString());
    }

    @Test
    void anUnrelatedSearchSaysNothingRelevantWasFound() {
        assertEquals(0, run("indicators", "search", "weather in Toronto"));

        assertTrue(out.toString().contains("Nothing relevant found"), out.toString());
    }

    @Test
    void casesSimilarFindsTheDecidedCaseWithItsDecision() {
        assertEquals(0, run("cases", "similar", "CAS-00003"));

        assertTrue(out.toString().contains("CAS-00001"), out.toString());
        assertTrue(out.toString().contains("ESCALATED."), out.toString());
    }

    @Test
    void aCaseWithADifferentPatternIsNotSimilar() {
        assertEquals(0, run("cases", "similar", "CAS-00002"));

        assertTrue(out.toString().contains("Nothing relevant found"), out.toString());
    }

    @Test
    void aCaseWithoutAFindingCannotBeCompared() {
        var failing = new ScriptedModel().then(new LLMException("invalid request", false, null));
        try (var facade = TraceWiseBootstrap.create(config(), () -> failing)) {
            facade.investigate("ALR-00004", null);
        }

        assertEquals(1, run("cases", "similar", "CAS-00004"));
        assertTrue(err.toString().contains("has no finding yet"), err.toString());
    }

    @Test
    void aDecidedCaseIsNotSimilarToItself() {
        assertEquals(0, run("cases", "similar", "CAS-00001"));

        assertTrue(out.toString().contains("Nothing relevant found"), out.toString());
    }
}
