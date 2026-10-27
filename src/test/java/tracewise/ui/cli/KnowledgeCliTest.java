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
import tracewise.llm.ModelConfig;
import tracewise.tools.ScenarioGenerator;

/** F04 from the CLI with the real embedding model: indicators search and cases similar. */
class KnowledgeCliTest {

    static final String STRUCTURING = "Multiple transactions conducted below the reporting threshold within a short period.";

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
        String caseId = "CAS-" + alertId.substring(4);
        var model = new ScriptedModel()
                .then(AgentRunnerTest.toolCall(tool, "{\"case_id\":\"" + caseId + "\",\"justification\":\"Because\"}"))
                .then(AgentRunnerTest.answer("{\"verdict\":\"ESCALATE\",\"confidence\":0.8,\"indicators\":[\""
                        + STRUCTURING + "\"],\"cited_transactions\":[],\"totals\":[],\"narrative\":\"" + narrative + "\"}"));
        var config = new AppConfig(dir.resolve("t.db"), "agent-run", ActorType.ANALYST,
                ModelConfig.of("anthropic", "claude-haiku-5-5", false), dir.resolve("replays"), dir.resolve(".env"));
        try (var facade = TraceWiseBootstrap.create(config, () -> model)) {
            facade.investigate(alertId, null);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        Path file = Files.write(dir.resolve("s.csv"), ScenarioGenerator.generate(42));
        assertEquals(0, run("import", file.toString()));
        assertEquals(0, run("monitor", "run"));
        agentFinds("ALR-00001", "propose_escalation", "Repeated cash deposits just under the reporting threshold.");
        assertEquals(0, run("approvals", "approve", "APR-00001"));
        agentFinds("ALR-00003", "set_priority", "Cash deposits just below the reporting threshold, repeated within days.");
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
    void aDecidedCaseIsNotSimilarToItself() {
        assertEquals(0, run("cases", "similar", "CAS-00001"));

        assertTrue(out.toString().contains("Nothing relevant found"), out.toString());
    }
}
