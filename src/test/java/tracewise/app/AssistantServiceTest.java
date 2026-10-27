package tracewise.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.agent.AgentRunnerTest;
import tracewise.agent.AgentRunnerTest.ScriptedModel;
import tracewise.agent.AssistantContext;
import tracewise.agent.OutcomeStatus;
import tracewise.domain.ActorType;
import tracewise.domain.Transaction;
import tracewise.llm.LLMException;
import tracewise.llm.LLMMessage;
import tracewise.llm.ModelConfig;
import tracewise.monitoring.AlertFilter;
import tracewise.persistence.Database;
import tracewise.persistence.SqliteTransactionRepository;
import tracewise.tools.ScenarioGenerator;
import tracewise.verification.VerificationStatus;

/** F10 through the real wiring with a scripted model. */
class AssistantServiceTest {

    @TempDir Path dir;
    final ScriptedModel model = new ScriptedModel();
    String account;
    String citation;

    TraceWiseFacade facade() {
        return TraceWiseBootstrap.create(new AppConfig(dir.resolve("t.db"), "kian", ActorType.ANALYST,
                ModelConfig.of("anthropic", "claude-haiku-5-5", false), dir.resolve("replays"), dir.resolve(".env")),
                () -> model);
    }

    /** An answer citing the first flagged transaction of the structuring alert correctly. */
    static String answerCiting(Transaction t, String account) {
        return """
                ```json
                {"kind": "ANSWER", "answer": "%s received a cash deposit of %s CAD.",
                 "cited_transactions": [{"transaction_id": "%s", "account_id": "%s", "amount_cad": %s, "date": "%s"}]}
                ```""".formatted(account, t.amountCad().toPlainString(), t.id(), account, t.amountCad().toPlainString(),
                LocalDate.ofInstant(t.timestamp(), ZoneOffset.UTC));
    }

    @BeforeEach
    void setUp() throws Exception {
        try (var f = facade()) {
            f.importTransactions(Files.write(dir.resolve("s.csv"), ScenarioGenerator.generate(42)));
            f.runMonitoring();
            var alert = f.listAlerts(new AlertFilter("R3", null, null)).get(0);
            account = alert.accountId();
            try (var db = Database.open(dir.resolve("t.db"))) {
                citation = answerCiting(new SqliteTransactionRepository(db).findTransaction(alert.transactionIds().get(0))
                        .orElseThrow(), account);
            }
        }
    }

    @Test
    void answersWithVerifiedCitations() {
        model.then(AgentRunnerTest.toolCall("account_history", "{\"account_id\":\"" + account + "\"}"))
                .then(AgentRunnerTest.answer(citation));
        try (var f = facade()) {
            var answer = f.ask("Why is " + account + " risky?", AssistantContext.NONE);

            assertEquals(OutcomeStatus.COMPLETED, answer.status());
            assertTrue(answer.text().contains(account));
            assertEquals(VerificationStatus.VERIFIED, answer.verification().status());
            assertEquals("account_history", answer.trace().steps().get(0).toolName());
        }
    }

    @Test
    void aFollowUpQuestionSeesTheEarlierExchange() {
        model.then(AgentRunnerTest.answer(citation)).then(AgentRunnerTest.answer(citation));
        try (var f = facade()) {
            f.ask("Why is " + account + " risky?", AssistantContext.NONE);
            f.ask("And who sent it money?", AssistantContext.NONE);
        }

        var opening = (LLMMessage.User) model.requests.get(1).messages().get(0);
        assertTrue(opening.text().contains("Why is " + account + " risky?"), opening.text());
        assertTrue(opening.text().endsWith("Question: And who sent it money?"), opening.text());
    }

    @Test
    void anActionToolIsNotRunEvenWhenTheModelNamesIt() {
        model.then(AgentRunnerTest.toolCall("propose_close", "{\"case_id\":\"CAS-00001\",\"justification\":\"j\"}"))
                .then(AgentRunnerTest.answer(citation));
        try (var f = facade()) {
            var answer = f.ask("Close case CAS-00001", AssistantContext.NONE);

            assertTrue(answer.trace().steps().get(0).error());
            assertTrue(f.pendingApprovals().isEmpty());
        }
    }

    @Test
    void aFailedRunIsAnErrorAndIsNotRemembered() {
        model.then(new LLMException("invalid request", false, null)).then(AgentRunnerTest.answer(citation));
        try (var f = facade()) {
            assertThrows(IllegalStateException.class, () -> f.ask("Why is it risky?", AssistantContext.NONE));
            f.ask("Second question", AssistantContext.NONE);
        }

        var opening = (LLMMessage.User) model.requests.get(1).messages().get(0);
        assertFalse(opening.text().contains("Why is it risky?"), opening.text());
    }

    @Test
    void aBlankQuestionIsRefused() {
        try (var f = facade()) {
            assertThrows(IllegalArgumentException.class, () -> f.ask(" ", AssistantContext.NONE));
        }
        assertEquals(List.of(), model.requests);
    }

    @Test
    void anUnreadableAnswerIsAnErrorAndIsNotRemembered() {
        model.then(AgentRunnerTest.answer("Just prose.")).then(AgentRunnerTest.answer("Still prose."))
                .then(AgentRunnerTest.answer(citation));
        try (var f = facade()) {
            var e = assertThrows(IllegalStateException.class, () -> f.ask("Why is it risky?", AssistantContext.NONE));
            assertTrue(e.getMessage().contains("required format"), e.getMessage());
            f.ask("Second question", AssistantContext.NONE);
        }

        var opening = (LLMMessage.User) model.requests.get(2).messages().get(0);
        assertFalse(opening.text().contains("Why is it risky?"), opening.text());
    }

    @Test
    void aWrongCitationIsReportedAsRejected() {
        model.then(AgentRunnerTest.answer(citation.replaceFirst("\"amount_cad\": [0-9.]+", "\"amount_cad\": 1.00")));
        try (var f = facade()) {
            var answer = f.ask("Why is " + account + " risky?", AssistantContext.NONE);

            assertEquals(VerificationStatus.PARTIALLY_VERIFIED, answer.verification().status());
            assertEquals(1, answer.verification().rejected().size());
        }
    }

    @Test
    void eachQuestionIsAuditedWithWhoAskedIt() {
        model.then(AgentRunnerTest.answer(citation));
        try (var f = facade()) {
            f.ask("Why is " + account + " risky?", new AssistantContext(null, account));
        }

        try (var db = Database.open(dir.resolve("t.db"))) {
            var entries = new tracewise.audit.AuditLog(new tracewise.audit.SqliteAuditRepository(db))
                    .query(new tracewise.audit.AuditFilter("ASSISTANT_ASKED", account));
            assertEquals(1, entries.size());
            assertEquals("kian", entries.get(0).actor().name());
            assertEquals("Why is " + account + " risky?", entries.get(0).details());
            assertEquals("COMPLETED", entries.get(0).outcome());
        }
    }
}
