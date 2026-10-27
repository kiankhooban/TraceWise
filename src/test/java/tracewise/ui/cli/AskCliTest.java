package tracewise.ui.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import tracewise.agent.AssistantContext;
import tracewise.app.AppConfig;
import tracewise.app.TraceWiseBootstrap;
import tracewise.domain.ActorType;
import tracewise.llm.ModelConfig;
import tracewise.llm.RecordingLLMClient;
import tracewise.tools.ScenarioGenerator;

/** tracewise ask: the Stage 3 entry point (JSON mode), replayed without a key. */
class AskCliTest {

    static final String QUESTION = "Which cases are open?";

    @TempDir Path dir;
    StringWriter out;
    StringWriter err;

    int run(String... args) {
        out = new StringWriter();
        err = new StringWriter();
        var all = new ArrayList<>(List.of("--db", dir.resolve("t.db").toString(), "--replays",
                dir.resolve("replays").toString()));
        all.addAll(List.of(args));
        return TraceWiseCli.commandLine(new PrintWriter(out, true), new PrintWriter(err, true))
                .execute(all.toArray(String[]::new));
    }

    @BeforeEach
    void recordAnAnswer() throws Exception {
        assertEquals(0, run("import", Files.write(dir.resolve("s.csv"), ScenarioGenerator.generate(42)).toString()));
        assertEquals(0, run("monitor", "run"));
        var model = new ScriptedModel()
                .then(AgentRunnerTest.toolCall("query_cases", "{\"status\":\"UNDER_INVESTIGATION\"}"))
                .then(AgentRunnerTest.answer("```json\n{\"kind\": \"ANSWER\", \"answer\": \"No case is under investigation.\", "
                        + "\"cited_transactions\": []}\n```"));
        var config = new AppConfig(dir.resolve("t.db"), "rec", ActorType.ANALYST,
                ModelConfig.of("anthropic", "claude-haiku-5-5", true), dir.resolve("replays"), dir.resolve(".env"));
        try (var facade = TraceWiseBootstrap.create(config,
                () -> new RecordingLLMClient(model, TraceWiseBootstrap.replayStore(dir.resolve("replays"))))) {
            facade.ask(QUESTION, AssistantContext.NONE);
        }
    }

    @Test
    void jsonModeGivesTheAnswerTraceAndVerification() throws Exception {
        assertEquals(0, run("--llm", "replay", "--model", "claude-haiku-5-5", "ask", "--json", QUESTION), err.toString());

        var json = new ObjectMapper().readTree(out.toString());
        assertEquals("COMPLETED", json.get("status").asText());
        assertEquals("ANSWER", json.get("kind").asText());
        assertEquals("No case is under investigation.", json.get("answer").asText());
        assertEquals("UNSUPPORTED", json.get("verification").get("status").asText());
        assertEquals("query_cases", json.get("trace").get("steps").get(0).get("toolName").asText());
        assertTrue(json.get("trace").has("costCad"));
    }

    @Test
    void textModePrintsTheAnswer() {
        assertEquals(0, run("--llm", "replay", "--model", "claude-haiku-5-5", "ask", "Which", "cases", "are", "open?"));

        assertTrue(out.toString().contains("No case is under investigation."), out.toString());
        assertTrue(out.toString().contains("Verification:"), out.toString());
    }

    @Test
    void aFailureExitsNonZeroWithAnError() throws Exception {
        assertEquals(1, run("--llm", "replay", "--model", "claude-haiku-5-5", "ask", "--json", "Never recorded?"));

        assertTrue(new ObjectMapper().readTree(out.toString()).has("error"), out.toString());
    }
}
