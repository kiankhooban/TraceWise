package tracewise.ui.cli;

import static org.junit.jupiter.api.Assertions.*;

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
import tracewise.cases.state.CaseStatus;
import tracewise.domain.ActorType;
import tracewise.llm.ModelConfig;
import tracewise.tools.ScenarioGenerator;

class ApprovalsCliTest {

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

    AppConfig config() {
        return new AppConfig(dir.resolve("t.db"), "agent-run", ActorType.ANALYST,
                ModelConfig.of("anthropic", "claude-haiku-5-5", false), dir.resolve("replays"), dir.resolve(".env"));
    }

    /** The agent investigates the alert and proposes the given action, then answers without citing anything. */
    void agentProposes(String alertId, String tool) {
        String caseId = "CAS-" + alertId.substring(4);
        var model = new ScriptedModel()
                .then(AgentRunnerTest.toolCall(tool, "{\"case_id\":\"" + caseId + "\",\"justification\":\"Because\"}"))
                .then(AgentRunnerTest.answer("{\"verdict\":\"ESCALATE\",\"confidence\":0.7,\"indicators\":[],"
                        + "\"cited_transactions\":[],\"totals\":[],\"narrative\":\"n\"}"));
        try (var facade = TraceWiseBootstrap.create(config(), () -> model)) {
            facade.investigate(alertId, null);
        }
    }

    CaseStatus caseStatus(String caseId) {
        try (var facade = TraceWiseBootstrap.create(config(), () -> null)) {
            return facade.getCase(caseId).status();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        Path file = Files.write(dir.resolve("s.csv"), ScenarioGenerator.generate(42));
        assertEquals(0, run("import", file.toString()));
        assertEquals(0, run("monitor", "run"));
        agentProposes("ALR-00001", "propose_escalation");
        agentProposes("ALR-00002", "propose_close");
    }

    @Test
    void listApproveAndReject() throws Exception {
        assertEquals(0, run("approvals", "list"));
        String list = out.toString();
        assertTrue(list.contains("APR-00001") && list.contains("Escalate case CAS-00001"), list);
        assertTrue(list.contains("APR-00002") && list.contains("Because"), list);
        assertTrue(list.contains("UNSUPPORTED"), "the finding behind it cited nothing: " + list);

        assertEquals(0, run("approvals", "list", "--json"));
        assertEquals(2, new ObjectMapper().readTree(out.toString()).size());

        assertEquals(0, run("approvals", "approve", "APR-00001"));
        assertTrue(out.toString().contains("APPROVED"), out.toString());
        assertEquals(CaseStatus.ESCALATED, caseStatus("CAS-00001"));

        assertEquals(1, run("approvals", "reject", "APR-00002", "--reason", " "));
        assertEquals(0, run("approvals", "reject", "APR-00002", "--reason", "Check the source of funds first"));
        assertEquals(CaseStatus.UNDER_INVESTIGATION, caseStatus("CAS-00002"));

        assertEquals(0, run("approvals", "list"));
        assertTrue(out.toString().contains("No proposals waiting"), out.toString());
    }

    @Test
    void decidingTwiceFails() {
        assertEquals(0, run("approvals", "approve", "APR-00001"));
        assertEquals(1, run("approvals", "approve", "APR-00001"));
        assertTrue(err.toString().contains("already APPROVED"), err.toString());
    }
}
