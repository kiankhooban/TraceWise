package tracewise.app;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import tracewise.audit.AuditEntry;
import tracewise.audit.AuditLog;
import tracewise.domain.Account;
import tracewise.domain.Actor;
import tracewise.domain.ActorType;
import tracewise.domain.Alert;
import tracewise.domain.AlertStatus;
import tracewise.domain.ThresholdReport;
import tracewise.domain.Transaction;
import tracewise.monitoring.AlertFilter;
import tracewise.monitoring.DetectionRule;
import tracewise.monitoring.MonitoringSummary;
import tracewise.monitoring.RiskScorer;
import tracewise.monitoring.RuleConfig;
import tracewise.monitoring.RuleKind;
import tracewise.monitoring.RuleMatch;
import tracewise.persistence.AlertRepository;
import tracewise.persistence.Ledger;
import tracewise.persistence.RuleConfigRepository;
import tracewise.persistence.ThresholdReportRepository;

/**
 * F02: runs every enabled rule (Strategy) over the ledger. Threshold matches go to the Threshold
 * Report Register; suspicion matches without an open alert become scored alerts.
 */
public class MonitoringService {

    private final Ledger ledger;
    private final List<DetectionRule> rules;
    private final AlertRepository alerts;
    private final ThresholdReportRepository thresholdReports;
    private final RuleConfigRepository ruleConfigs;
    private final RiskScorer scorer;
    private final AuditLog audit;
    private final Actor actor;

    public MonitoringService(Ledger ledger, List<DetectionRule> rules, AlertRepository alerts,
            ThresholdReportRepository thresholdReports, RuleConfigRepository ruleConfigs, RiskScorer scorer,
            AuditLog audit, Actor actor) {
        this.ledger = ledger;
        this.rules = List.copyOf(rules);
        this.alerts = alerts;
        this.thresholdReports = thresholdReports;
        this.ruleConfigs = ruleConfigs;
        this.scorer = scorer;
        this.audit = audit;
        this.actor = actor;
        for (DetectionRule rule : rules) {
            ruleConfigs.load(rule.id()).ifPresent(rule::configure);
        }
    }

    public MonitoringSummary run() {
        List<Account> accounts = ledger.accounts();
        if (accounts.isEmpty()) {
            throw new IllegalStateException("no transactions loaded; import a file first");
        }
        var matches = new ArrayList<RuleMatch>();
        var failed = new ArrayList<String>();
        for (DetectionRule rule : rules) {
            if (!rule.config().enabled()) {
                continue;
            }
            try {
                matches.addAll(rule.evaluate(ledger));
            } catch (RuntimeException e) {
                failed.add(rule.id() + ": " + e.getMessage());
            }
        }

        int reports = 0;
        var newSuspicions = new ArrayList<RuleMatch>();
        for (RuleMatch m : matches) {
            if (m.kind() == RuleKind.THRESHOLD) {
                reports += thresholdReports.save(toReport(m)) ? 1 : 0;
            } else if (!alerts.hasOpenAlert(m)) {
                newSuspicions.add(m);
            }
        }

        Map<String, Account> byId = accounts.stream().collect(Collectors.toMap(Account::id, a -> a));
        Map<String, Set<String>> rulesPerAccount = new HashMap<>();
        newSuspicions.forEach(m -> rulesPerAccount.computeIfAbsent(m.accountId(), k -> new HashSet<>()).add(m.ruleId()));
        var byRule = new TreeMap<String, Integer>();
        for (RuleMatch m : newSuspicions) {
            int risk = scorer.score(m, byId.get(m.accountId()), rulesPerAccount.get(m.accountId()).size());
            alerts.save(new Alert(null, m.ruleId(), m.accountId(), m.transactions().stream().map(Transaction::id)
                    .toList(), m.totalCad(), risk, priority(risk), AlertStatus.NEW, Instant.now()));
            byRule.merge(m.ruleId(), 1, Integer::sum);
        }

        var summary = new MonitoringSummary(reports, newSuspicions.size(), byRule, failed);
        audit.append(new AuditEntry(Instant.now(), actor, "MONITORING_RUN", "all enabled rules",
                summary + (failed.isEmpty() ? "" : "; failed: " + String.join(", ", failed)),
                failed.isEmpty() ? "OK" : "PARTIAL"));
        return summary;
    }

    public List<Alert> listAlerts(AlertFilter filter) {
        return alerts.find(filter);
    }

    public List<ThresholdReport> listThresholdReports() {
        return thresholdReports.findAll();
    }

    public List<DetectionRule> listRules() {
        return rules;
    }

    /** Validates and applies the change; an invalid configuration throws and the previous one stays. */
    public void updateRule(String ruleId, RuleConfig config) {
        if (actor.type() != ActorType.SUPERVISOR) {
            throw new IllegalStateException("only a Compliance Supervisor can change monitoring rules");
        }
        DetectionRule rule = rules.stream().filter(r -> r.id().equals(ruleId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no rule with id " + ruleId));
        RuleConfig before = rule.config();
        rule.configure(config);
        ruleConfigs.save(ruleId, config);
        audit.append(new AuditEntry(Instant.now(), actor, "RULE_UPDATED", ruleId,
                describe(before) + " -> " + describe(config), "OK"));
    }

    private static String describe(RuleConfig c) {
        return (c.enabled() ? "enabled " : "disabled ") + new TreeMap<>(c.parameters()).entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue().toPlainString()).collect(Collectors.joining(", "));
    }

    private static ThresholdReport toReport(RuleMatch m) {
        String id = "TR-" + m.ruleId() + "-" + m.accountId() + "-" + m.windowStart().getEpochSecond();
        return new ThresholdReport(id, m.ruleId(), m.accountId(), m.transactions().stream().map(Transaction::id)
                .toList(), m.totalCad(), m.windowStart(), m.windowEnd());
    }

    private static int priority(int risk) {
        return risk >= 70 ? 1 : risk >= 40 ? 2 : 3;
    }
}
