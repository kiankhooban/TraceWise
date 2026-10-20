package tracewise.actions;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.agent.tools.ActionScope;
import tracewise.agent.tools.ToolRegistry;
import tracewise.app.TraceWiseBootstrap;
import tracewise.cases.state.CaseStatus;
import tracewise.llm.ToolCall;

class ActionToolsTest {

    @TempDir Path dir;
    ActionsFixture f;
    ToolRegistry tools;
    ActionScope scope;

    @BeforeEach
    void open() {
        f = new ActionsFixture(dir);
        scope = new ActionScope(f.cases);
        tools = TraceWiseBootstrap.investigationTools(f.accounts, f.alerts, f.cases, f.dispatcher, scope);
        scope.begin("CAS-00001");
    }

    @AfterEach
    void close() {
        f.close();
    }

    String call(String tool, String args) {
        var result = tools.execute(new ToolCall("c", tool, args));
        assertFalse(result.error(), result.content());
        return result.content();
    }

    @Test
    void everyActionToolRequiresAJustification() {
        for (String name : java.util.List.of("set_priority", "link_alerts", "add_to_watchlist", "create_task",
                "propose_close", "propose_escalation")) {
            var result = tools.execute(new ToolCall("c", name, "{}"));
            assertTrue(result.error() && result.content().contains("missing required argument"), name + ": " + result.content());
            assertTrue(tools.schemas(java.util.Set.of(name)).get(0).parameters().stream()
                    .anyMatch(p -> p.name().equals("justification") && p.required()), name);
        }
    }

    @Test
    void autonomousToolsActAndProposalToolsOnlyPropose() {
        assertTrue(call("set_priority", "{\"alert_id\":\"ALR-00001\",\"priority\":1,\"justification\":\"large\"}")
                .startsWith("Done"));
        assertEquals(1, f.alerts.findById("ALR-00001").orElseThrow().priority());
        call("link_alerts", "{\"case_id\":\"CAS-00001\",\"alert_ids\":\"ALR-00002\",\"justification\":\"same funds\"}");
        assertTrue(f.cases.findById("CAS-00001").orElseThrow().alertIds().contains("ALR-00002"));
        call("add_to_watchlist", "{\"account_id\":\"C3\",\"justification\":\"feeds C1\"}");
        call("create_task", "{\"case_id\":\"CAS-00001\",\"description\":\"Check KYC\",\"justification\":\"no ID\"}");

        String proposed = call("propose_close", "{\"case_id\":\"CAS-00001\",\"justification\":\"payroll\"}");
        assertTrue(proposed.contains("approval") && !proposed.contains("APR-"),
                "the agent is not told the global proposal id, which would make replays depend on run order: " + proposed);
        assertEquals(CaseStatus.PENDING_APPROVAL, f.caseStatus());
    }

    @Test
    void aRejectedActionIsAnErrorTheAgentCanReadAndFix() {
        var result = tools.execute(new ToolCall("c", "set_priority",
                "{\"alert_id\":\"ALR-00001\",\"priority\":7,\"justification\":\"x\"}"));
        assertTrue(result.error());
        assertTrue(result.content().contains("priority must be 1"), result.content());
    }

    @Test
    void linkedAlertsShowsAlertsOnTheAccountAndItsCounterparties() throws Exception {
        var json = new ObjectMapper().readTree(call("linked_alerts", "{\"account_id\":\"C1\"}"));
        var alerts = json.get("alerts");
        assertEquals(2, alerts.size());
        assertEquals("ALR-00001", alerts.get(0).get("alert_id").asText());
        assertEquals("CAS-00001", alerts.get(0).get("case_id").asText());
        assertEquals("ALR-00002", alerts.get(1).get("alert_id").asText());
        assertEquals("C3", alerts.get(1).get("account_id").asText());
    }

    @Test
    void theAgentMayActOnlyOnTheCaseItIsInvestigating() {
        f.cases.save(new tracewise.domain.Case("CAS-00002", java.time.Instant.EPOCH, java.util.List.of("ALR-00002"),
                CaseStatus.UNDER_INVESTIGATION));
        var otherCase = tools.execute(new ToolCall("c", "propose_close", "{\"case_id\":\"CAS-00002\",\"justification\":\"x\"}"));
        assertTrue(otherCase.error() && otherCase.content().contains("CAS-00001"), otherCase.content());
        assertEquals(CaseStatus.UNDER_INVESTIGATION, f.cases.findById("CAS-00002").orElseThrow().status());

        var otherAlert = tools.execute(new ToolCall("c", "set_priority",
                "{\"alert_id\":\"ALR-00002\",\"priority\":1,\"justification\":\"x\"}"));
        assertTrue(otherAlert.error(), otherAlert.content());

        scope.end();
        var noRun = tools.execute(new ToolCall("c", "set_priority",
                "{\"alert_id\":\"ALR-00001\",\"priority\":1,\"justification\":\"x\"}"));
        assertTrue(noRun.error() && noRun.content().contains("no investigation"), noRun.content());
    }
}
