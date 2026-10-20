package tracewise.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.agent.AgentFinding;
import tracewise.agent.AgentOutcome;
import tracewise.agent.AgentTrace;
import tracewise.agent.Claim;
import tracewise.agent.OutcomeStatus;
import tracewise.agent.TotalClaim;
import tracewise.agent.TraceStep;
import tracewise.agent.Verdict;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Case;
import tracewise.llm.ModelConfig;
import tracewise.llm.TokenUsage;
import tracewise.verification.ClaimCheck;
import tracewise.verification.VerificationReport;
import tracewise.verification.VerificationStatus;

class SqliteCaseRepositoryTest {

    @TempDir Path dir;
    Database db;
    SqliteCaseRepository repo;

    @BeforeEach
    void open() {
        db = Database.open(dir.resolve("c.db"));
        repo = new SqliteCaseRepository(db);
    }

    @AfterEach
    void close() {
        db.close();
    }

    static AgentFinding finding() {
        var trace = new AgentTrace("ALR-00001", new ModelConfig("anthropic", "claude-sonnet-5-5", new BigDecimal("2"),
                new BigDecimal("10"), false), new BigDecimal("1.37"));
        trace.record(new TraceStep("ALR-00001", 1, "account_history", "{\"account_id\":\"S-C1\"}", "[...]", false, 14));
        trace.addUsage(new TokenUsage(5200, 410));
        trace.finish(new AgentOutcome("ALR-00001", OutcomeStatus.INCOMPLETE, "{...}"));
        var f = new AgentFinding(Verdict.ESCALATE, 0.8, List.of("Structuring"),
                List.of(new Claim("t1", "S-C1", new BigDecimal("9500.00"), LocalDate.parse("2026-09-01"))),
                List.of(new TotalClaim("Deposits", new BigDecimal("28500"), List.of("t1", "t2", "t3"))),
                "Three deposits.", true, trace);
        f.attachVerification(new VerificationReport(VerificationStatus.PARTIALLY_VERIFIED, List.of(
                new ClaimCheck("Transaction t1", true, ""), new ClaimCheck("Deposits", false, "sum is 28000"))));
        return f;
    }

    @Test
    void caseIsFoundByIdAndByAlertInItsSavedState() {
        var c = new Case("CAS-00001", Instant.parse("2026-10-20T12:00:00Z"), List.of("ALR-00001"));
        c.startInvestigation();
        repo.save(c);
        c.submitForApproval();
        repo.save(c);

        var loaded = repo.findByAlert("ALR-00001").orElseThrow();
        assertEquals("CAS-00001", loaded.id());
        assertEquals(CaseStatus.PENDING_APPROVAL, loaded.status());
        assertEquals(c.openedAt(), loaded.openedAt());
        assertEquals(List.of("ALR-00001"), repo.findById("CAS-00001").orElseThrow().alertIds());
        assertTrue(repo.findByAlert("ALR-00009").isEmpty());
        assertEquals(1, repo.findAll().size());
    }

    @Test
    void findingsAndTheirTracesSurviveARoundTrip() {
        var c = new Case("CAS-00001", Instant.EPOCH, List.of("ALR-00001"));
        var saved = finding();
        c.addFinding(saved);
        repo.save(c);
        repo.save(c); // saving again must not duplicate the finding

        var loaded = repo.findById("CAS-00001").orElseThrow();
        assertEquals(1, loaded.findings().size());
        var f = loaded.latestFinding();
        assertEquals(Verdict.ESCALATE, f.verdict());
        assertEquals(0.8, f.confidence());
        assertEquals(saved.indicators(), f.indicators());
        assertEquals(saved.claims(), f.claims());
        assertEquals(saved.totals(), f.totals());
        assertEquals("Three deposits.", f.narrative());
        assertTrue(f.incomplete());
        assertEquals(saved.verification(), f.verification());

        var t = f.trace();
        assertEquals(saved.trace().steps(), t.steps());
        assertEquals(saved.trace().usage(), t.usage());
        assertEquals(saved.trace().outcome(), t.outcome());
        assertEquals(0, saved.trace().costCad().compareTo(t.costCad()));
        assertEquals("claude-sonnet-5-5", t.pricing().modelName());
    }
}
