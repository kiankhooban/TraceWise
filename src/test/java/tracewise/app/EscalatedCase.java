package tracewise.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import tracewise.agent.AgentRunnerTest;
import tracewise.agent.AgentRunnerTest.ScriptedModel;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Transaction;
import tracewise.monitoring.AlertFilter;
import tracewise.persistence.Database;
import tracewise.persistence.SqliteTransactionRepository;
import tracewise.tools.ScenarioGenerator;

/**
 * Test setup for F11: the sample data imported and monitored, and CAS-00001 (alert ALR-00001) escalated with a
 * finding that cites the alert's first transaction correctly and TX-99999, which does not exist.
 */
public record EscalatedCase(String subject, Transaction cited) {

    public static EscalatedCase create(TraceWiseFacade f, Path dir, Path db, ScriptedModel model) throws Exception {
        f.importTransactions(Files.write(dir.resolve("s.csv"), ScenarioGenerator.generate(42)));
        f.runMonitoring();
        var alert = f.listAlerts(AlertFilter.ALL).stream().filter(a -> a.id().equals("ALR-00001")).findFirst()
                .orElseThrow();
        Transaction cited;
        try (var d = Database.open(db)) {
            cited = new SqliteTransactionRepository(d).findTransaction(alert.transactionIds().get(0)).orElseThrow();
        }
        model.then(AgentRunnerTest.toolCall("propose_escalation",
                "{\"case_id\":\"CAS-00001\",\"justification\":\"Cash deposits under the threshold\"}"))
                .then(AgentRunnerTest.answer("""
                        ```json
                        {"verdict": "ESCALATE", "confidence": 0.9, "indicators": [],
                         "cited_transactions": [
                           {"transaction_id": "%s", "account_id": "%s", "amount_cad": %s, "date": "%s"},
                           {"transaction_id": "TX-99999", "account_id": "%s", "amount_cad": 9900, "date": "2026-09-01"}],
                         "totals": [], "narrative": "Repeated deposits under the threshold, including TX-99999."}
                        ```""".formatted(cited.id(), alert.accountId(), cited.amountCad().toPlainString(),
                        LocalDate.ofInstant(cited.timestamp(), ZoneOffset.UTC), alert.accountId())));
        f.investigate("ALR-00001", null);
        f.approve(f.pendingApprovals().get(0).id());
        assertEquals(CaseStatus.ESCALATED, f.getCase("CAS-00001").status());
        return new EscalatedCase(alert.accountId(), cited);
    }
}
