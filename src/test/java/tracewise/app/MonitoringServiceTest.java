package tracewise.app;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.audit.AuditFilter;
import tracewise.audit.AuditLog;
import tracewise.audit.SqliteAuditRepository;
import tracewise.domain.Actor;
import tracewise.domain.ActorType;
import tracewise.domain.Alert;
import tracewise.domain.AlertStatus;
import tracewise.ingest.CurrencyConverter;
import tracewise.ingest.ScenarioCsvFormat;
import tracewise.monitoring.*;
import tracewise.persistence.Database;
import tracewise.persistence.Ledger;
import tracewise.persistence.SqliteAlertRepository;
import tracewise.persistence.SqliteRuleConfigRepository;
import tracewise.persistence.SqliteThresholdReportRepository;
import tracewise.persistence.SqliteTransactionRepository;
import tracewise.tools.ScenarioGenerator;

class MonitoringServiceTest {

    @TempDir Path dir;
    Database db;
    SqliteTransactionRepository transactions;
    AuditLog audit;
    final Actor supervisor = new Actor("sam", ActorType.SUPERVISOR);

    @BeforeEach
    void setUp() {
        db = Database.open(dir.resolve("m.db"));
        transactions = new SqliteTransactionRepository(db);
        audit = new AuditLog(new SqliteAuditRepository(db));
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    MonitoringService service(List<DetectionRule> rules) {
        return new MonitoringService(transactions, rules, new SqliteAlertRepository(db),
                new SqliteThresholdReportRepository(db), new SqliteRuleConfigRepository(db), new RiskScorer(), audit,
                supervisor);
    }

    static List<DetectionRule> allRules() {
        return List.of(new LargeCashRule(), new InternationalTransferRule(), new StructuringRule(),
                new PassThroughRule(), new FanInRule(), new FanOutRule());
    }

    void importScenarios() throws IOException {
        Path file = Files.write(dir.resolve("s.csv"), ScenarioGenerator.generate(42));
        var rates = CurrencyConverter.bankOfCanadaDefaults();
        new ImportService(transactions, List.of(new ScenarioCsvFormat(rates)), audit, supervisor).importFile(file);
    }

    @Test
    void findsExactlyThePlantedSchemesAndNothingElse() throws IOException {
        importScenarios();
        var monitoring = service(allRules());
        MonitoringSummary summary = monitoring.run();

        assertEquals(4, summary.thresholdReports());
        assertEquals(Map.of("R3", 1, "R4", 1, "R5", 1, "R6", 1), summary.alertsByRule());
        assertEquals("4 threshold reports; 4 new alerts: R3 1, R4 1, R5 1, R6 1", summary.toString());

        var subjects = monitoring.listAlerts(AlertFilter.ALL).stream().map(Alert::accountId).sorted().toList();
        assertEquals(List.of("F-C1", "O-C1", "P-C1", "S-C1"), subjects);
        var reportSubjects = monitoring.listThresholdReports().stream().map(r -> r.ruleId() + ":" + r.accountId())
                .sorted().toList();
        assertEquals(List.of("R1:L-C1", "R1:L-C2", "R2:I-C1", "R2:I-C2"), reportSubjects);
        assertEquals("OK", audit.query(new AuditFilter("MONITORING_RUN", null)).get(0).outcome());
    }

    @Test
    void alertsAreNewSortedByRiskAndFilterable() throws IOException {
        importScenarios();
        var monitoring = service(allRules());
        monitoring.run();
        var alerts = monitoring.listAlerts(AlertFilter.ALL);
        assertTrue(alerts.stream().allMatch(a -> a.status() == AlertStatus.NEW && a.id().startsWith("ALR-")));
        for (int i = 1; i < alerts.size(); i++) {
            assertTrue(alerts.get(i - 1).riskScore() >= alerts.get(i).riskScore());
        }
        assertEquals(1, monitoring.listAlerts(new AlertFilter("R3", null, null)).size());
        assertEquals(4, monitoring.listAlerts(new AlertFilter(null, AlertStatus.NEW, null)).size());
        assertTrue(monitoring.listAlerts(new AlertFilter(null, null, new BigDecimal("1000000"))).isEmpty());
        assertEquals(List.of("STRUCT"), monitoring.listAlerts(new AlertFilter("R3", null, null)).get(0)
                .transactionIds().stream().map(id -> id.split("-")[0]).distinct().toList());
    }

    @Test
    void aSecondRunCreatesNoDuplicates() throws IOException {
        importScenarios();
        var monitoring = service(allRules());
        monitoring.run();
        var again = monitoring.run();
        assertEquals(0, again.thresholdReports());
        assertEquals(0, again.newAlerts());
        assertEquals("No new threshold reports or alerts", again.toString());
        assertEquals(4, monitoring.listAlerts(AlertFilter.ALL).size());
    }

    @Test
    void refusesToRunWithoutTransactions() {
        var e = assertThrows(IllegalStateException.class, () -> service(allRules()).run());
        assertEquals("no transactions loaded; import a file first", e.getMessage());
    }

    @Test
    void aFailingRuleIsReportedAndTheOthersStillRun() throws IOException {
        importScenarios();
        DetectionRule broken = new FanOutRule() {
            @Override
            public String id() {
                return "R9";
            }

            @Override
            protected List<tracewise.domain.Transaction> candidates(Ledger ledger, tracewise.domain.Account account) {
                throw new IllegalStateException("boom");
            }
        };
        var summary = service(List.of(broken, new FanInRule())).run();
        assertEquals(List.of("R9: boom"), summary.failedRules());
        assertEquals(Map.of("R5", 1), summary.alertsByRule());
    }

    @Test
    void ruleChangesAreValidatedPersistedAndAudited() throws IOException {
        importScenarios();
        var monitoring = service(allRules());
        var params = new HashMap<>(new FanInRule().config().parameters());
        params.put("min_senders", new BigDecimal("7"));
        monitoring.updateRule("R5", new RuleConfig(true, params));

        params.put("min_senders", new BigDecimal("-1"));
        assertThrows(InvalidConfigurationException.class, () -> monitoring.updateRule("R5", new RuleConfig(true, params)));
        assertThrows(IllegalArgumentException.class, () -> monitoring.updateRule("R99", new RuleConfig(true, params)));

        var reloaded = service(allRules());
        assertEquals(new BigDecimal("7"), reloaded.listRules().stream().filter(r -> r.id().equals("R5")).findFirst()
                .orElseThrow().config().parameter("min_senders"));
        assertFalse(reloaded.run().alertsByRule().containsKey("R5"));
        var entry = audit.query(new AuditFilter("RULE_UPDATED", "R5")).get(0);
        assertEquals("sam", entry.actor().name());
        assertTrue(entry.details().contains("min_senders=5") && entry.details().contains("min_senders=7"), entry.details());
    }

    @Test
    void disabledRulesDoNotRun() throws IOException {
        importScenarios();
        var monitoring = service(allRules());
        monitoring.updateRule("R6", new RuleConfig(false, new FanOutRule().config().parameters()));
        assertFalse(monitoring.run().alertsByRule().containsKey("R6"));
    }

    @Test
    void onlyASupervisorCanChangeRules() {
        var asAnalyst = new MonitoringService(transactions, allRules(), new SqliteAlertRepository(db),
                new SqliteThresholdReportRepository(db), new SqliteRuleConfigRepository(db), new RiskScorer(), audit,
                new Actor("kian", ActorType.ANALYST));
        var e = assertThrows(IllegalStateException.class,
                () -> asAnalyst.updateRule("R5", new FanInRule().config()));
        assertEquals("only a Compliance Supervisor can change monitoring rules", e.getMessage());
    }
}
