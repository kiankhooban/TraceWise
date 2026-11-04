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
            Style st = style();
            var now = java.time.Instant.now();
            for (PendingAction p : facade().pendingApprovals()) {
                String by = p.proposedBy().isHuman() ? "HUMAN" : p.proposedBy().type().name();
                out().println();
                out().println("  " + st.bold(p.id()) + "  " + p.command().describe() + "  " + st.dim("proposed by " + by
                        + " · waiting " + Style.age(java.time.Duration.between(p.proposedAt(), now))));
                String caseId = p.command().targetCaseId().orElse(null);
                AgentFinding f = caseId == null ? null : facade().getCase(caseId).latestFinding();
                out().println(st.field("Finding", f == null ? "no finding yet"
                        : st.verdict(f.verdict()) + " " + f.confidence() + "  " + st.check(f.verification())));
                out().println(st.field("If yes", ifApproved(p)));
                out().println(st.field("Why", p.justification()));
            }
            out().println();
            out().println(st.dim("  Decide: approvals approve <id>, or approvals reject <id> --reason \"...\""));
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

    /** What approving does, in the GUI's words (ProposalRow). */
    static String ifApproved(PendingAction p) {
        if (p.command() instanceof tracewise.actions.EscalateCaseCommand e) {
            return e.caseId() + " becomes Escalated";
        }
        if (p.command() instanceof tracewise.actions.CloseCaseCommand c) {
            return c.caseId() + " becomes Closed and its alerts close";
        }
        return p.command().describe();
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
