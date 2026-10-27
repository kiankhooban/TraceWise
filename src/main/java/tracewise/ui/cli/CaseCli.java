package tracewise.ui.cli;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import tracewise.agent.AgentFinding;
import tracewise.audit.AuditEntry;
import tracewise.domain.Case;

@Command(name = "case", description = "One case: show it, add a note, close or reopen it (F08).",
        subcommands = {CaseCli.Show.class, CaseCli.Note.class, CaseCli.Close.class, CaseCli.Reopen.class})
public class CaseCli {

    @Command(name = "show", description = "The case: state, alerts, latest finding, tasks, notes and history.")
    static class Show extends CliCommandBase {

        @Parameters(index = "0", description = "The case id, for example CAS-00001.")
        String caseId;

        @Override
        protected Object execute() {
            Case c = facade().getCase(caseId);
            var r = new LinkedHashMap<String, Object>();
            r.put("caseId", c.id());
            r.put("status", c.status());
            r.put("alerts", c.alertIds());
            AgentFinding f = c.latestFinding();
            r.put("finding", f == null ? null : Map.of("verdict", f.verdict(), "confidence", f.confidence(),
                    "verification", f.verification().status() + ", " + f.verification().summary(), "narrative", f.narrative()));
            r.put("tasks", c.tasks());
            r.put("notes", c.notes());
            r.put("history", facade().caseHistory(caseId));
            return r;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void print(Object result) {
            if (json) {
                super.print(result);
                return;
            }
            var r = (Map<String, Object>) result;
            out().println(r.get("caseId") + "  " + r.get("status") + "  alerts " + String.join(", ", (List<String>) r.get("alerts")));
            var f = (Map<String, Object>) r.get("finding");
            out().println(f == null ? "No finding yet." : "Finding: " + f.get("verdict") + ", confidence " + f.get("confidence")
                    + "; verification " + f.get("verification") + "\n  " + f.get("narrative"));
            ((List<?>) r.get("tasks")).forEach(t -> out().println("Task: " + t));
            ((List<tracewise.domain.CaseNote>) r.get("notes")).forEach(n -> out().println("Note (" + n.author() + ", "
                    + n.createdAt() + "): " + n.text()));
            out().println("History:");
            for (AuditEntry e : (List<AuditEntry>) r.get("history")) {
                out().println("  " + e.timestamp() + "  " + e.actor().name() + "  " + e.eventType() + "  " + e.target()
                        + "  " + (e.details() == null ? "" : e.details().replaceAll("\\s+", " ")));
            }
        }
    }

    @Command(name = "note", description = "Add a note to the case.")
    static class Note extends CliCommandBase {

        @Parameters(index = "0", description = "The case id.")
        String caseId;

        @Parameters(index = "1..*", description = "The note text.")
        List<String> words;

        @Override
        protected Object execute() {
            facade().addNote(caseId, String.join(" ", words));
            return "Note added to " + caseId;
        }
    }

    @Command(name = "close", description = "Close the case as a false alarm (an analyst's decision; reason required).")
    static class Close extends CliCommandBase {

        @Parameters(index = "0", description = "The case id.")
        String caseId;

        @Option(names = "--reason", required = true, description = "Why it is a false alarm.")
        String reason;

        @Override
        protected Object execute() {
            facade().closeCase(caseId, reason);
            return caseId + " closed";
        }
    }

    @Command(name = "reopen", description = "Return a closed case to Open (reason required).")
    static class Reopen extends CliCommandBase {

        @Parameters(index = "0", description = "The case id.")
        String caseId;

        @Option(names = "--reason", required = true, description = "Why it is reopened, for example new evidence.")
        String reason;

        @Override
        protected Object execute() {
            facade().reopenCase(caseId, reason);
            return caseId + " reopened";
        }
    }
}
