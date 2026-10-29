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
                out().printf("  [turn %d] %s %s -> %s (%d ms)%n", s.turn(), s.toolName(), s.arguments().replaceAll("\\s*\\n\\s*", " "),
                        s.error() ? "ERROR: " + s.result() : "ok", s.durationMs());
            }

            @Override
            public void onFinished(AgentOutcome outcome) {
            }
        };
        if (!json) {
            out().println("Investigating " + alertId + "...");
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
        out().println("Case " + r.caseId() + ": " + r.status());
        AgentFinding f = r.finding();
        if (f == null) {
            out().println(r.message());
        } else {
            out().println("Verdict: " + f.verdict() + ", confidence " + f.confidence() + (f.incomplete() ? " (INCOMPLETE)" : ""));
            out().println("Verification: " + f.verification().status() + ", " + f.verification().summary());
            for (ClaimCheck c : f.verification().rejected()) {
                out().println("  REJECTED " + c.claim() + ": " + c.reason());
            }
            out().println("Indicators: " + String.join("; ", f.indicators()));
            out().println("Narrative: " + f.narrative());
        }
        AgentTrace t = r.trace();
        out().printf("Model %s: %d input + %d output tokens, CAD %s%n", t.pricing().modelName(), t.usage().inputTokens(),
                t.usage().outputTokens(), t.costCad().setScale(4, RoundingMode.HALF_UP).toPlainString());
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
