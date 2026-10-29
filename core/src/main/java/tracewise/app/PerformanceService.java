package tracewise.app;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import tracewise.actions.PendingAction;
import tracewise.actions.ProposalStatus;
import tracewise.agent.AgentFinding;
import tracewise.agent.OutcomeStatus;
import tracewise.agent.Verdict;
import tracewise.audit.AuditEntry;
import tracewise.audit.AuditFilter;
import tracewise.audit.AuditLog;
import tracewise.domain.ActorType;
import tracewise.domain.Case;
import tracewise.metrics.AlertLabel;
import tracewise.metrics.Measure;
import tracewise.metrics.MetricsFilter;
import tracewise.metrics.PerformanceMetrics;
import tracewise.metrics.RulePrecision;
import tracewise.persistence.AlertRepository;
import tracewise.persistence.ApprovalRepository;
import tracewise.persistence.CaseRepository;

/**
 * F12: measures the agent from stored findings, run outcomes, proposals and ground-truth labels. Read-only and
 * deterministic: it measures the AI but uses none.
 */
public class PerformanceService {

    private final CaseRepository cases;
    private final AlertRepository alerts;
    private final ApprovalRepository approvals;
    private final AuditLog audit;

    public PerformanceService(CaseRepository cases, AlertRepository alerts, ApprovalRepository approvals,
            AuditLog audit) {
        this.cases = cases;
        this.alerts = alerts;
        this.approvals = approvals;
        this.audit = audit;
    }

    /** Everything the metrics are computed from, loaded once for all models. */
    private record Records(List<Case> cases, Map<String, AlertLabel> labels, List<PendingAction> proposals,
            List<AuditEntry> runs) {
    }

    public PerformanceMetrics compute(MetricsFilter filter) {
        return compute(filter, load(filter));
    }

    /** All models first, then one entry per model that produced a finding, by name. */
    public List<PerformanceMetrics> computeByModel(MetricsFilter filter) {
        Records records = load(filter);
        var models = new TreeSet<String>();
        records.cases().forEach(c -> c.findings().forEach(f -> models.add(model(f))));
        // a failed run stores no finding, so a model that only failed is known from the audit trail alone
        records.runs().stream().map(run -> InvestigationService.modelOf(run.details())).filter(m -> m != null)
                .forEach(models::add);
        var result = new ArrayList<PerformanceMetrics>(List.of(compute(filter.withModel(null), records)));
        models.forEach(m -> result.add(compute(filter.withModel(m), records)));
        return result;
    }

    private Records load(MetricsFilter filter) {
        Map<String, AlertLabel> labels = alerts.launderingLabels().stream()
                .collect(Collectors.toMap(AlertLabel::alertId, Function.identity()));
        var runs = audit.query(new AuditFilter("INVESTIGATION_FINISHED", null, filter.range().from(),
                filter.range().to(), null));
        return new Records(cases.findAll(), labels, approvals.findAll(), runs);
    }

    private PerformanceMetrics compute(MetricsFilter filter, Records r) {
        int tp = 0, fp = 0, fn = 0, tn = 0, inconclusive = 0, inconclusiveLaundering = 0, runs = 0, turns = 0, claims = 0, confirmed = 0;
        BigDecimal cost = BigDecimal.ZERO;
        int[] unclear = new int[Verdict.values().length];
        for (Case c : r.cases()) {
            if (!ruleMatches(filter, r.labels().get(c.openingAlertId()))) {
                continue;
            }
            List<AgentFinding> matching = c.findings().stream().filter(f -> findingMatches(filter, f)).toList();
            for (AgentFinding f : matching) {
                runs++;
                turns += f.trace().turns();
                cost = cost.add(f.trace().costCad());
                if (f.verification() != null) {
                    claims += f.verification().checks().size();
                    confirmed += f.verification().checks().size() - f.verification().rejected().size();
                }
            }
            // judged by the alert the agent was asked about: alerts it linked later must not make it look right
            AlertLabel opening = r.labels().get(c.openingAlertId());
            Boolean laundering = opening == null ? null : opening.laundering();
            if (matching.isEmpty() || laundering == null) {
                continue;
            }
            // the case's latest verdict is what the bank acted on; earlier runs were superseded
            Verdict v = matching.get(matching.size() - 1).verdict();
            if (!laundering && Boolean.TRUE.equals(opening.accountLaundering())) {
                // a clean alert on an account that launders elsewhere: neither right nor wrong to escalate
                unclear[v.ordinal()]++;
                continue;
            }
            if (v == Verdict.INCONCLUSIVE) {
                inconclusive++;
                if (laundering) {
                    inconclusiveLaundering++;
                }
            } else if (v == Verdict.ESCALATE) {
                if (laundering) tp++; else fp++;
            } else {
                if (laundering) fn++; else tn++;
            }
        }
        return new PerformanceMetrics(filter.model(), runs, tp, fp, fn, tn, inconclusive, inconclusiveLaundering,
                unclear[Verdict.ESCALATE.ordinal()], unclear[Verdict.DISMISS.ordinal()],
                unclear[Verdict.INCONCLUSIVE.ordinal()],
                Measure.ratio(tp, tp + fp), Measure.ratio(tp, tp + fn),
                Measure.ratio(tp, tp + fn + inconclusiveLaundering), new Measure(claims == 0 ? null : (double) confirmed / claims, runs),
                new Measure(runs == 0 ? null : (double) turns / runs, runs),
                runs == 0 ? null : cost.divide(BigDecimal.valueOf(runs), MathContext.DECIMAL64),
                failureRate(filter, r), overrideRate(filter, r), rulePrecision(filter, r));
    }

    /** Runs that ended FAILED among those not cancelled, from the audit trail (a failed run stores no finding). */
    private static Measure failureRate(MetricsFilter filter, Records r) {
        int ended = 0, failed = 0;
        for (AuditEntry run : r.runs()) {
            if (run.outcome().equals(OutcomeStatus.CANCELLED.name()) || !ruleMatches(filter, r.labels().get(run.target()))
                    || (filter.model() != null && !filter.model().equals(InvestigationService.modelOf(run.details())))) {
                continue;
            }
            ended++;
            if (run.outcome().equals(OutcomeStatus.FAILED.name())) {
                failed++;
            }
        }
        return Measure.ratio(failed, ended);
    }

    /** The agent's proposals a person decided, and the share they rejected. Expired and waiting ones are left out. */
    private static Measure overrideRate(MetricsFilter filter, Records r) {
        Map<String, Case> byId = r.cases().stream().collect(Collectors.toMap(Case::id, Function.identity()));
        int decided = 0, rejected = 0;
        for (PendingAction p : r.proposals()) {
            Case c = p.command().targetCaseId().map(byId::get).orElse(null);
            if (p.proposedBy().type() != ActorType.AGENT || c == null || !filter.range().contains(p.proposedAt())
                    || (p.status() != ProposalStatus.APPROVED && p.status() != ProposalStatus.REJECTED)
                    || !ruleMatches(filter, r.labels().get(c.openingAlertId()))
                    || (filter.model() != null && !filter.model().equals(proposingModel(c, p.proposedAt())))) {
                continue;
            }
            decided++;
            if (p.status() == ProposalStatus.REJECTED) {
                rejected++;
            }
        }
        return Measure.ratio(rejected, decided);
    }

    /** The model of the run that made a proposal: the first of the case's runs to finish after it. */
    private static String proposingModel(Case c, Instant proposedAt) {
        return c.findings().stream().filter(f -> f.trace().finishedAt() != null
                && !f.trace().finishedAt().isBefore(proposedAt)).findFirst().map(PerformanceService::model).orElse(null);
    }

    /** For each rule, the share of its labelled alerts that involve labelled laundering; the agent plays no part. */
    private static List<RulePrecision> rulePrecision(MetricsFilter filter, Records r) {
        var byRule = new TreeMap<String, int[]>();
        for (AlertLabel a : r.labels().values()) {
            if (!ruleMatches(filter, a) || !filter.range().contains(a.createdAt())) {
                continue;
            }
            int[] counts = byRule.computeIfAbsent(a.ruleId(), k -> new int[3]);
            counts[0]++;
            if (a.laundering() != null) {
                counts[1]++;
                if (a.laundering()) {
                    counts[2]++;
                }
            }
        }
        return byRule.entrySet().stream()
                .map(e -> new RulePrecision(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2])).toList();
    }

    private static boolean findingMatches(MetricsFilter filter, AgentFinding f) {
        if (filter.model() != null && !filter.model().equals(model(f))) {
            return false;
        }
        Instant at = f.trace().finishedAt();
        // a run stored without a finish time can only be placed in an unbounded range
        return at == null ? filter.range().equals(tracewise.domain.DateRange.ALL) : filter.range().contains(at);
    }

    private static boolean ruleMatches(MetricsFilter filter, AlertLabel alert) {
        return filter.ruleId() == null || (alert != null && filter.ruleId().equals(alert.ruleId()));
    }

    private static String model(AgentFinding f) {
        return f.trace().pricing().runLabel();
    }
}
