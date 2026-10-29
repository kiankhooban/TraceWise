package tracewise.app;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.actions.CloseCaseCommand;
import tracewise.actions.EscalateCaseCommand;
import tracewise.actions.PendingAction;
import tracewise.actions.ProposalStatus;
import tracewise.agent.AgentFinding;
import tracewise.agent.AgentOutcome;
import tracewise.agent.AgentTrace;
import tracewise.agent.OutcomeStatus;
import tracewise.agent.Verdict;
import tracewise.audit.AuditEntry;
import tracewise.audit.AuditLog;
import tracewise.audit.SqliteAuditRepository;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Actor;
import tracewise.domain.ActorType;
import tracewise.domain.Alert;
import tracewise.domain.AlertStatus;
import tracewise.domain.Case;
import tracewise.domain.DateRange;
import tracewise.domain.PaymentChannel;
import tracewise.domain.Transaction;
import tracewise.llm.ModelConfig;
import tracewise.llm.TokenUsage;
import tracewise.metrics.Measure;
import tracewise.metrics.MetricsCsv;
import tracewise.metrics.MetricsFilter;
import tracewise.metrics.PerformanceMetrics;
import tracewise.persistence.Database;
import tracewise.persistence.SqliteAlertRepository;
import tracewise.persistence.SqliteApprovalRepository;
import tracewise.persistence.SqliteCaseRepository;
import tracewise.persistence.SqliteTransactionRepository;
import tracewise.verification.ClaimCheck;
import tracewise.verification.VerificationReport;
import tracewise.verification.VerificationStatus;

/** F12: how well the agent does, against ground-truth labels where they exist and from its own records. */
class PerformanceServiceTest {

    static final Instant T0 = Instant.parse("2026-09-01T00:00:00Z");
    static final String HAIKU = "claude-haiku-5-5";
    static final String SONNET = "claude-sonnet-5-5";
    static final Actor ANALYST = new Actor("ana", ActorType.ANALYST);

    @TempDir Path dir;
    Database db;
    SqliteAlertRepository alerts;
    SqliteCaseRepository cases;
    SqliteApprovalRepository approvals;
    SqliteAuditRepository audit;
    PerformanceService service;
    int alertCount;
    int proposalCount;

    @BeforeEach
    void open() {
        db = Database.open(dir.resolve("p.db"));
        alerts = new SqliteAlertRepository(db);
        cases = new SqliteCaseRepository(db);
        approvals = new SqliteApprovalRepository(db);
        audit = new SqliteAuditRepository(db);
        service = new PerformanceService(cases, alerts, approvals, new AuditLog(audit));
        // L: labelled laundering, N: labelled not laundering, U: no label (like the scenario-free formats)
        // each transaction is the only one of its account (S plus its id), except A1, which shares L1's account
        new SqliteTransactionRepository(db).saveAll(List.of(tx("L1", true), tx("L2", true), tx("N1", false),
                tx("N2", false), tx("U1", null), tx("A1", "SL1", false)));
    }

    @AfterEach
    void close() {
        db.close();
    }

    static Transaction tx(String id, Boolean laundering) {
        return tx(id, "S" + id, laundering);
    }

    static Transaction tx(String id, String sender, Boolean laundering) {
        return new Transaction(id, T0, sender, "R" + id, BigDecimal.TEN, BigDecimal.TEN, "CAD", PaymentChannel.WIRE,
                "CA", laundering);
    }

    /** Stores an alert on the sender of its first transaction and returns its id. */
    String alert(String rule, String... transactionIds) {
        String account = transactionIds[0].equals("A1") ? "SL1" : "S" + transactionIds[0];
        alerts.save(new Alert(null, rule, account, List.of(transactionIds), BigDecimal.TEN, 50, 2, AlertStatus.IN_CASE,
                T0));
        return String.format("ALR-%05d", ++alertCount);
    }

    /** The alert's case (CAS- plus the alert number) with these findings, oldest first. */
    String caseFor(String alertId, AgentFinding... findings) {
        String id = "CAS-" + alertId.substring(4);
        cases.save(new Case(id, T0, List.of(alertId), CaseStatus.UNDER_INVESTIGATION, List.of(findings)));
        return id;
    }

    /** cost: 1M input tokens is CAD 0.10 on Haiku and CAD 2 on Sonnet (one CAD per USD here). */
    static AgentFinding finding(Verdict verdict, String model, int turns, int inputTokens, int confirmed, int rejected,
            Instant finishedAt) {
        var trace = AgentTrace.restore("ALR", ModelConfig.of("anthropic", model, false), BigDecimal.ONE, List.of(),
                new TokenUsage(inputTokens, 0), turns, new AgentOutcome("ALR", OutcomeStatus.COMPLETED, "{}"), finishedAt);
        var f = new AgentFinding(verdict, 0.9, List.of(), List.of(), List.of(), "n", false, trace);
        var checks = new ArrayList<ClaimCheck>();
        for (int i = 0; i < confirmed + rejected; i++) {
            checks.add(new ClaimCheck("claim " + i, i < confirmed, i < confirmed ? "" : "no such transaction"));
        }
        f.attachVerification(new VerificationReport(VerificationStatus.VERIFIED, checks));
        return f;
    }

    static AgentFinding finding(Verdict verdict) {
        return finding(verdict, HAIKU, 3, 1_000_000, 1, 0, T0);
    }

    static AgentFinding finding(Verdict verdict, String model, Instant finishedAt) {
        return finding(verdict, model, 3, 1_000_000, 1, 0, finishedAt);
    }

    void run(String alertId, String model, String outcome) {
        audit.append(new AuditEntry(T0, ANALYST, "INVESTIGATION_FINISHED", alertId,
                InvestigationService.runDetails(model, "ESCALATE, confidence 0.9"), outcome));
    }

    void proposal(String caseId, Actor by, Instant at, ProposalStatus status) {
        approvals.save(new PendingAction(String.format("APR-%05d", ++proposalCount),
                status == ProposalStatus.APPROVED ? new EscalateCaseCommand(caseId) : new CloseCaseCommand(caseId),
                "why", by, at, status, status == ProposalStatus.PENDING || status == ProposalStatus.EXPIRED ? null : ANALYST,
                null, status == ProposalStatus.PENDING ? null : at.plusSeconds(60)));
    }

    static void assertMeasure(Double value, int sampleSize, Measure m) {
        assertEquals(sampleSize, m.sampleSize(), m.toString());
        if (value == null) {
            assertNull(m.value(), m.toString());
        } else {
            assertEquals(value, m.value(), 1e-9, m.toString());
        }
    }

    @Test
    void eachVerdictIsComparedWithTheLabelsOfItsAlertsTransactions() {
        caseFor(alert("R4", "L1"), finding(Verdict.ESCALATE)); // true positive
        caseFor(alert("R5", "N1"), finding(Verdict.ESCALATE)); // false positive
        caseFor(alert("R6", "L2"), finding(Verdict.DISMISS)); // false negative
        caseFor(alert("R4", "N2"), finding(Verdict.DISMISS)); // true negative
        caseFor(alert("R5", "U1"), finding(Verdict.ESCALATE)); // no label: not compared
        caseFor(alert("R6", "N1", "L1"), finding(Verdict.INCONCLUSIVE)); // laundering if any transaction is

        PerformanceMetrics m = service.compute(MetricsFilter.ALL);

        assertEquals(1, m.truePositives());
        assertEquals(1, m.falsePositives());
        assertEquals(1, m.falseNegatives());
        assertEquals(1, m.trueNegatives());
        assertEquals(1, m.inconclusive());
        assertMeasure(0.5, 2, m.precision());
        assertMeasure(0.5, 2, m.recall());
        assertEquals(6, m.sampleSize());
        assertFalse(m.precision().meaningful(), "fewer than 10 findings");
        // an inconclusive verdict on laundering is a miss for the stricter recall
        assertEquals(1, m.inconclusiveLaundering());
        assertMeasure(1.0 / 3, 3, m.strictRecall());
        var counts = m.rows().stream().filter(r -> r.metric().equals("inconclusive and laundering")).findFirst()
                .orElseThrow();
        assertEquals("1", counts.value());
        assertEquals(5, counts.sampleSize(), "labelled cases");
    }

    /**
     * Found in the IBM baseline: 10 of 12 "wrong" escalations were on accounts that launder in other transactions. A
     * clean alert on such an account is neither right nor wrong to escalate, so it is counted apart, not as a false
     * positive; a negative is a clean alert on a clean account.
     */
    @Test
    void aCleanAlertOnAnAccountThatLaundersElsewhereIsCountedApart() {
        caseFor(alert("R4", "A1"), finding(Verdict.ESCALATE));
        caseFor(alert("R4", "N1"), finding(Verdict.ESCALATE));
        caseFor(alert("R5", "A1"), finding(Verdict.DISMISS));
        caseFor(alert("R6", "A1"), finding(Verdict.INCONCLUSIVE));

        var m = service.compute(MetricsFilter.ALL);

        assertEquals(1, m.falsePositives(), "only the clean alert on a clean account");
        assertEquals(0, m.trueNegatives(), "a dismissal on an account that launders elsewhere is not a true negative");
        assertEquals(1, m.unclearEscalated());
        assertEquals(1, m.unclearDismissed());
        assertEquals(1, m.unclearInconclusive());
        assertMeasure(0.0, 1, m.precision());
        var row = m.rows().stream().filter(r -> r.metric().equals("escalated, clean alert on a laundering account"))
                .findFirst().orElseThrow();
        assertEquals("1", row.value());
    }

    /** Found in the live IBM run: the agent links many alerts, which must not make its verdicts look right. */
    @Test
    void theVerdictIsJudgedByTheAlertThatOpenedTheCaseNotByAlertsLinkedLater() {
        String opening = alert("R4", "N1");
        String linked = alert("R5", "L1");
        cases.save(new Case("CAS-" + opening.substring(4), T0, List.of(opening, linked), CaseStatus.UNDER_INVESTIGATION,
                List.of(finding(Verdict.ESCALATE))));

        var m = service.compute(MetricsFilter.ALL);

        assertEquals(0, m.truePositives());
        assertEquals(1, m.falsePositives());
    }

    @Test
    void aCaseCountsOnceWithItsLatestVerdictButEveryRunCountsForTurnsAndCost() {
        caseFor(alert("R4", "L1"), finding(Verdict.DISMISS, HAIKU, 2, 1_000_000, 1, 0, T0),
                finding(Verdict.ESCALATE, HAIKU, 4, 3_000_000, 1, 0, T0.plusSeconds(60)));

        var m = service.compute(MetricsFilter.ALL);

        assertEquals(1, m.truePositives());
        assertEquals(0, m.falseNegatives());
        assertMeasure(1.0, 1, m.recall());
        assertEquals(2, m.sampleSize());
        assertMeasure(3.0, 2, m.averageTurns());
        assertEquals(0, new BigDecimal("0.2").compareTo(m.averageCostCad()), m.averageCostCad().toString());
    }

    @Test
    void withoutLabelsPrecisionAndRecallAreNotAvailableAndTheRestIsComputed() {
        caseFor(alert("R4", "U1"), finding(Verdict.ESCALATE, HAIKU, 3, 1_000_000, 3, 1, T0));

        var m = service.compute(MetricsFilter.ALL);

        assertMeasure(null, 0, m.precision());
        assertMeasure(null, 0, m.recall());
        assertMeasure(0.75, 1, m.verificationRate());
        var precision = m.rows().stream().filter(r -> r.metric().equals("precision")).findFirst().orElseThrow();
        assertEquals("not available", precision.value());
    }

    @Test
    void theVerificationRateIsTheShareOfAllClaimsConfirmed() {
        caseFor(alert("R4", "L1"), finding(Verdict.ESCALATE, HAIKU, 3, 1, 3, 1, T0));
        caseFor(alert("R4", "N1"), finding(Verdict.DISMISS, HAIKU, 3, 1, 1, 0, T0));

        assertMeasure(0.8, 2, service.compute(MetricsFilter.ALL).verificationRate());
    }

    @Test
    void failuresAreRunsThatEndedFailedAmongThoseNotCancelled() {
        String a = alert("R4", "L1");
        run(a, HAIKU, "COMPLETED");
        run(a, HAIKU, "FAILED");
        run(a, HAIKU, "INCOMPLETE");
        run(a, HAIKU, "CANCELLED");
        run(a, SONNET, "COMPLETED");

        assertMeasure(0.25, 4, service.compute(MetricsFilter.ALL).failureRate());
        assertMeasure(1.0 / 3, 3, service.compute(MetricsFilter.ALL.withModel(HAIKU)).failureRate());
        assertMeasure(0.0, 1, service.compute(MetricsFilter.ALL.withModel(SONNET)).failureRate());
    }

    @Test
    void anOverrideIsAProposalOfTheAgentThatAPersonRejected() {
        String c = caseFor(alert("R4", "L1"), finding(Verdict.ESCALATE, HAIKU, T0.plusSeconds(3600)));
        proposal(c, Actor.AGENT, T0, ProposalStatus.APPROVED);
        proposal(c, Actor.AGENT, T0, ProposalStatus.REJECTED);
        proposal(c, Actor.AGENT, T0, ProposalStatus.REJECTED);
        proposal(c, Actor.AGENT, T0, ProposalStatus.EXPIRED); // nobody decided
        proposal(c, Actor.AGENT, T0, ProposalStatus.PENDING);
        proposal(c, ANALYST, T0, ProposalStatus.REJECTED); // a person's own request is not the agent's

        assertMeasure(2.0 / 3, 3, service.compute(MetricsFilter.ALL).overrideRate());
    }

    @Test
    void aProposalCountsForTheModelWhoseRunMadeIt() {
        String c = caseFor(alert("R4", "L1"), finding(Verdict.ESCALATE, HAIKU, T0.plusSeconds(3600)),
                finding(Verdict.ESCALATE, SONNET, T0.plusSeconds(3 * 3600)));
        proposal(c, Actor.AGENT, T0.plusSeconds(1800), ProposalStatus.REJECTED); // during the Haiku run
        proposal(c, Actor.AGENT, T0.plusSeconds(2 * 3600), ProposalStatus.APPROVED); // during the Sonnet run

        assertMeasure(1.0, 1, service.compute(MetricsFilter.ALL.withModel(HAIKU)).overrideRate());
        assertMeasure(0.0, 1, service.compute(MetricsFilter.ALL.withModel(SONNET)).overrideRate());
        assertMeasure(0.5, 2, service.compute(MetricsFilter.ALL).overrideRate());
    }

    @Test
    void theBreakdownListsAllModelsFirstThenEachModel() {
        caseFor(alert("R4", "L1"), finding(Verdict.ESCALATE, SONNET, T0));
        caseFor(alert("R4", "N1"), finding(Verdict.ESCALATE, HAIKU, T0));

        List<PerformanceMetrics> all = service.computeByModel(MetricsFilter.ALL);

        assertEquals(3, all.size());
        assertNull(all.get(0).model());
        assertMeasure(0.5, 2, all.get(0).precision());
        assertEquals(HAIKU, all.get(1).model());
        assertMeasure(0.0, 1, all.get(1).precision());
        assertEquals(SONNET, all.get(2).model());
        assertMeasure(1.0, 1, all.get(2).precision());
        assertEquals(0, new BigDecimal("2").compareTo(all.get(2).averageCostCad()));
    }

    /** A failed run stores no finding, so a model that only failed would otherwise have no column at all. */
    @Test
    void aModelWhoseRunsAllFailedStillGetsItsOwnEntry() {
        String a = alert("R4", "L1");
        caseFor(a, finding(Verdict.ESCALATE, HAIKU, T0));
        run(a, HAIKU, "COMPLETED");
        run(a, SONNET, "FAILED");

        var all = service.computeByModel(MetricsFilter.ALL);

        assertEquals(List.of(HAIKU, SONNET), all.stream().skip(1).map(PerformanceMetrics::model).toList());
        assertMeasure(1.0, 1, all.get(2).failureRate());
        assertEquals(0, all.get(2).sampleSize());
    }

    /** Replays answer from recordings, so they must never count as a live run of the configured model. */
    @Test
    void replayedRunsAreKeptApartFromLiveRunsOfTheSameModel() {
        var replayed = finding(Verdict.ESCALATE, HAIKU, T0);
        var trace = AgentTrace.restore("ALR", ModelConfig.of("replay", HAIKU, false), BigDecimal.ONE, List.of(),
                new TokenUsage(1, 0), 2, new AgentOutcome("ALR", OutcomeStatus.COMPLETED, "{}"), T0);
        var f = new AgentFinding(Verdict.ESCALATE, 0.9, List.of(), List.of(), List.of(), "n", false, trace);
        caseFor(alert("R4", "L1"), replayed);
        caseFor(alert("R4", "N1"), f);

        assertEquals(List.of(HAIKU, HAIKU + " (replay)"),
                service.computeByModel(MetricsFilter.ALL).stream().skip(1).map(PerformanceMetrics::model).toList());
    }

    /** Findings and audit entries written before F12 have no finish time and no model name. */
    @Test
    void recordsFromBeforeF12CountForAllModelsButNotForAModelOrADateRange() {
        String a = alert("R4", "L1");
        caseFor(a, finding(Verdict.ESCALATE, HAIKU, null));
        audit.append(new AuditEntry(T0, ANALYST, "INVESTIGATION_FINISHED", a, "The run failed: disk full", "FAILED"));
        run(a, HAIKU, "COMPLETED");

        assertEquals(1, service.compute(MetricsFilter.ALL).sampleSize());
        assertEquals(0, service.compute(new MetricsFilter(new DateRange(T0, T0.plusSeconds(60)), null, null)).sampleSize());
        assertMeasure(0.5, 2, service.compute(MetricsFilter.ALL).failureRate());
        assertMeasure(0.0, 1, service.compute(MetricsFilter.ALL.withModel(HAIKU)).failureRate());
        assertNull(InvestigationService.modelOf("The run failed: disk full"));
        assertEquals(HAIKU + " (replay)", InvestigationService.modelOf(InvestigationService.runDetails(HAIKU + " (replay)", "x: y")));
    }

    /** The CLI and the GUI show the same grid: a metric per row, a model per column. */
    @Test
    void theTableHasAColumnPerModelWithItsRunCount() {
        caseFor(alert("R4", "L1"), finding(Verdict.ESCALATE, HAIKU, T0));

        var table = PerformanceMetrics.table(service.computeByModel(MetricsFilter.ALL));

        assertEquals(List.of("metric", "all models (1 run)", HAIKU + " (1 run)"), table.get(0));
        assertEquals(List.of("precision", "1.000 (n=1, too few)", "1.000 (n=1, too few)"), table.get(1));
    }

    @Test
    void perRulePrecisionCoversEveryAlertNotOnlyTheInvestigatedOnes() {
        alert("R4", "L1");
        alert("R4", "N1");
        alert("R4", "U1");
        alert("R5", "N2");
        alert("R6", "L2", "N2");

        var rules = service.compute(MetricsFilter.ALL.withModel(SONNET)).rulePrecision();

        assertEquals(List.of("R4", "R5", "R6"), rules.stream().map(r -> r.ruleId()).toList());
        assertEquals(3, rules.get(0).alerts());
        assertMeasure(0.5, 2, rules.get(0).precision());
        assertMeasure(0.0, 1, rules.get(1).precision());
        assertMeasure(1.0, 1, rules.get(2).precision());
    }

    @Test
    void theRuleAndDateFiltersSelectCasesRunsAndAlerts() {
        String early = alert("R4", "L1");
        caseFor(early, finding(Verdict.ESCALATE, HAIKU, T0));
        caseFor(alert("R5", "N1"), finding(Verdict.ESCALATE, HAIKU, T0.plus(Duration.ofDays(10))));
        run(early, HAIKU, "FAILED");

        var r4 = service.compute(new MetricsFilter(DateRange.ALL, null, "R4"));
        assertEquals(1, r4.sampleSize());
        assertMeasure(1.0, 1, r4.precision());
        assertMeasure(1.0, 1, r4.failureRate());
        assertEquals(List.of("R4"), r4.rulePrecision().stream().map(r -> r.ruleId()).toList());

        var later = service.compute(new MetricsFilter(new DateRange(T0.plus(Duration.ofDays(5)), T0.plus(Duration.ofDays(20))),
                null, null));
        assertEquals(1, later.sampleSize());
        assertMeasure(0.0, 1, later.precision());
        assertMeasure(null, 0, later.failureRate());
        assertTrue(later.rulePrecision().isEmpty(), "every alert was raised before the range");
    }

    @Test
    void aMeasureFromFewerThanTenIsMarkedNotMeaningful() {
        assertFalse(new Measure(0.5, 9).meaningful());
        assertTrue(new Measure(0.5, 10).meaningful());
    }

    @Test
    void metricsExportAsCsvOneRowPerModelAndMetric() throws Exception {
        caseFor(alert("R4", "L1"), finding(Verdict.ESCALATE, HAIKU, T0));
        caseFor(alert("R4", "N1"), finding(Verdict.ESCALATE, HAIKU, T0));
        var out = new StringWriter();

        MetricsCsv.write(service.computeByModel(MetricsFilter.ALL), out);

        String csv = out.toString();
        assertTrue(csv.startsWith("model,metric,value,sample_size,meaningful\r\n"), csv);
        assertTrue(csv.contains("all models,precision,0.500,2,no\r\n"), csv);
        assertTrue(csv.contains("claude-haiku-5-5,precision,0.500,2,no\r\n"), csv);
        assertTrue(csv.contains("all models,R4 alert precision,0.500,2,no\r\n"), csv);
    }
}
