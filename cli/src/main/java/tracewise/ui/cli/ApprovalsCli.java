package tracewise.ui.cli;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import tracewise.actions.PendingAction;
import tracewise.actions.ProposalStatus;
import tracewise.agent.AgentFinding;

@Command(name = "approvals", description = "Actions the agent proposed that wait for a human decision.",
        subcommands = {ApprovalsCli.ListApprovals.class, ApprovalsCli.Approve.class, ApprovalsCli.Reject.class})
public class ApprovalsCli {

    @Command(name = "list", description = "List waiting proposals with the finding behind each.")
    static class ListApprovals extends CliCommandBase {

        @Override
        protected Object execute() {
            return facade().pendingApprovals().stream().map(p -> row(p, finding(p))).toList();
        }

        private String finding(PendingAction p) {
            AgentFinding f = p.command().targetCaseId().map(id -> facade().getCase(id).latestFinding()).orElse(null);
            return f == null ? "no finding yet"
                    : f.verdict() + " " + f.confidence() + ", " + f.verification().status() + " ("
                    + f.verification().summary() + ")";
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void print(Object result) {
            if (json) {
                super.print(result);
                return;
            }
            var rows = (List<Map<String, Object>>) result;
            if (rows.isEmpty()) {
                out().println("No proposals waiting");
                return;
            }
            for (var r : rows) {
                out().printf("%s  %s%n  proposed by %s at %s%n  why: %s%n  finding: %s%n", r.get("id"), r.get("action"),
                        r.get("proposedBy"), r.get("proposedAt"), r.get("justification"), r.get("finding"));
            }
        }
    }

    @Command(name = "approve", description = "Approve a proposal: it runs now, or expires if the case has changed.")
    static class Approve extends CliCommandBase {

        @Parameters(index = "0", description = "The proposal id, for example APR-00001.")
        String id;

        @Override
        protected Object execute() {
            return decided(facade().approve(id));
        }
    }

    @Command(name = "reject", description = "Reject a proposal; the case goes back to investigation.")
    static class Reject extends CliCommandBase {

        @Parameters(index = "0", description = "The proposal id, for example APR-00001.")
        String id;

        @Option(names = "--reason", required = true, description = "Why it is rejected (required).")
        String reason;

        @Override
        protected Object execute() {
            return decided(facade().reject(id, reason));
        }
    }

    static Map<String, Object> row(PendingAction p, String finding) {
        var r = new LinkedHashMap<String, Object>();
        r.put("id", p.id());
        r.put("action", p.command().describe());
        r.put("caseId", p.command().targetCaseId().orElse(null));
        r.put("justification", p.justification());
        r.put("proposedBy", p.proposedBy().name());
        r.put("proposedAt", p.proposedAt());
        r.put("finding", finding);
        return r;
    }

    /** The outcome of a decision; JSON fields in --json mode, one line otherwise. */
    record Decision(String id, ProposalStatus status, String action, String reason) {
        @Override
        public String toString() {
            return id + " " + status + ": " + action + (reason == null ? "" : " (" + reason + ")");
        }
    }

    static Decision decided(PendingAction p) {
        return new Decision(p.id(), p.status(), p.command().describe(), p.decisionReason());
    }
}
