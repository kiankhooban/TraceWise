package tracewise.ui.cli;

import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import tracewise.agent.AgentEventListener;
import tracewise.agent.AgentFinding;
import tracewise.agent.AgentOutcome;
import tracewise.agent.AgentTrace;
import tracewise.agent.InvestigationResult;
import tracewise.agent.TraceStep;
import tracewise.verification.ClaimCheck;

@Command(name = "investigate", description = "Let the agent investigate an alert, verify its finding and store it on the case.")
public class InvestigateCli extends CliCommandBase {

    @Parameters(index = "0", description = "The alert id, for example ALR-00001.")
    String alertId;

    @Override
    protected Object execute() {
        AgentEventListener steps = json ? null : new AgentEventListener() {
            @Override
            public void onStep(TraceStep s) {
                out().println(style().step(s));
            }

            @Override
            public void onFinished(AgentOutcome outcome) {
            }
        };
        if (!json) {
            Style st = style();
            // an unknown alert gets no header: the facade's error says what is wrong
            facade().listAlerts(tracewise.monitoring.AlertFilter.ALL).stream().filter(a -> a.id().equals(alertId)).findFirst()
                    .ifPresent(a -> {
                        out().println("  " + st.bold(a.id()) + "  " + Style.rule(a.ruleId()) + "  " + a.accountId() + "  "
                                + st.risk(a.riskScore()) + "  " + Style.money(a.totalCad()) + " CAD in "
                                + a.transactionIds().size() + (a.transactionIds().size() == 1 ? " transaction" : " transactions"));
                        out().println(st.dim("  The agent is investigating; each step it takes is listed as it happens."));
                        out().println();
                    });
        }
        return facade().investigate(alertId, steps);
    }

    @Override
    protected void print(Object result) {
        var r = (InvestigationResult) result;
        if (json) {
            super.print(toJson(r));
            return;
        }
        Style st = style();
        out().println();
        if (r.status() != tracewise.agent.OutcomeStatus.COMPLETED) {
            out().println(st.field("Run", st.waiting(r.status().name())));
        }
        AgentFinding f = r.finding();
        if (f == null) {
            out().println(st.wrap(String.valueOf(r.message()), 2));
        } else {
            out().println(st.field("Verdict", st.verdict(f.verdict()) + "  confidence " + f.confidence()
                    + (f.incomplete() ? "  " + st.waiting("INCOMPLETE") : "")));
            printChecked(f.verification());
            for (int i = 0; i < f.indicators().size(); i++) {
                out().println(i == 0 ? st.field("FINTRAC", f.indicators().get(i)) : st.wrap(f.indicators().get(i), 12));
            }
        }
        if (r.caseId() != null) {
            var waiting = facade().pendingApprovals().stream()
                    .filter(p -> r.caseId().equals(p.command().targetCaseId().orElse(null))).map(p -> p.id()).toList();
            out().println(st.field("Case", r.caseId() + "  " + facade().getCase(r.caseId()).status().name().replace('_', ' ')
                    + (waiting.isEmpty() ? "" : ", waiting for a person as " + String.join(", ", waiting))));
        }
        if (f != null) {
            out().println();
            out().println(st.wrap(f.narrative(), 2));
        }
        out().println();
        out().println(st.dim("  " + cost(r.trace()) + (r.caseId() == null ? "" : " · next: case show " + r.caseId()
                + ", approvals list")));
    }

    /** VER 6/6 and what it means, then each rejected claim with why (F05). */
    private void printChecked(tracewise.verification.VerificationReport v) {
        Style st = style();
        out().println(st.field("Checked", st.check(v) + "  " + v.summary()));
        for (ClaimCheck c : v.rejected()) {
            out().println(st.wrap(st.bad("REJ") + " " + c.claim() + ": " + c.reason(), 12));
        }
    }

    /** "model · 22,779 in + 2,920 out tokens · CAD 0.0053" */
    static String cost(AgentTrace t) {
        return t.pricing().modelName() + " · " + String.format("%,d", t.usage().inputTokens()) + " in + "
                + String.format("%,d", t.usage().outputTokens()) + " out tokens · CAD "
                + t.costCad().setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private static Map<String, Object> toJson(InvestigationResult r) {
        var out = new LinkedHashMap<String, Object>();
        out.put("caseId", r.caseId());
        out.put("status", r.status());
        out.put("message", r.message());
        AgentFinding f = r.finding();
        if (f != null) {
            var finding = new LinkedHashMap<String, Object>();
            finding.put("verdict", f.verdict());
            finding.put("confidence", f.confidence());
            finding.put("incomplete", f.incomplete());
            finding.put("indicators", f.indicators());
            finding.put("claims", f.claims());
            finding.put("totals", f.totals());
            finding.put("narrative", f.narrative());
            finding.put("verification", Map.of("status", f.verification().status(), "summary", f.verification().summary(),
                    "checks", f.verification().checks()));
            out.put("finding", finding);
        }
        AgentTrace t = r.trace();
        var trace = new LinkedHashMap<String, Object>();
        trace.put("model", t.pricing().modelName());
        trace.put("usage", t.usage());
        trace.put("costCad", t.costCad());
        trace.put("steps", t.steps());
        out.put("trace", trace);
        return out;
    }
}
