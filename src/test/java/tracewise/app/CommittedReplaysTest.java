package tracewise.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.agent.OutcomeStatus;
import tracewise.agent.TriageReport;
import tracewise.domain.ActorType;
import tracewise.llm.ModelConfig;
import tracewise.monitoring.AlertFilter;
import tracewise.verification.VerificationStatus;

/** The recordings in data/replays still answer the agent's requests on the shipped sample data. */
class CommittedReplaysTest {

    @TempDir Path dir;

    TraceWiseFacade replaying() {
        var facade = TraceWiseBootstrap.create(new AppConfig(dir.resolve("t.db"), "grader", ActorType.ANALYST,
                ModelConfig.of("replay", "claude-haiku-5-5", false), Path.of("data/replays"), dir.resolve(".env")));
        if (facade.listAlerts(AlertFilter.ALL).isEmpty()) {
            facade.importTransactions(Path.of("data/samples/scenarios.csv"));
            facade.runMonitoring();
        }
        return facade;
    }

    @Test
    void theFourSampleAlertsReplayInAnyOrderWithEveryClaimConfirmed() {
        try (var facade = replaying()) {
            var single = facade.investigate("ALR-00004", null);
            assertEquals(OutcomeStatus.COMPLETED, single.status(), single.message());
            assertEquals(VerificationStatus.VERIFIED, single.finding().verification().status());

            TriageReport report = facade.triage(TriageLimits.DEFAULT, null);

            assertEquals(3, report.processed(), report.summary());
            assertEquals(0, report.failed(), report.summary());
            for (var item : report.items()) {
                var finding = facade.getCase(item.caseId()).latestFinding();
                assertEquals(VerificationStatus.VERIFIED, finding.verification().status(),
                        item.alertId() + ": " + finding.verification().rejected());
            }
        }
    }

    @Test
    void afterADecisionTheRecordingsStillReplay() {
        try (var facade = replaying()) {
            facade.investigate("ALR-00004", null);
            facade.approve(facade.pendingApprovals().get(0).id());

            var report = facade.triage(TriageLimits.DEFAULT, null);

            assertEquals(0, report.failed(), report.failures().toString());
            assertEquals(3, report.processed());
        }
    }
}
