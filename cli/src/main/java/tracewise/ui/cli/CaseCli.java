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
            Style st = style();
            String status = r.get("status").toString().replace('_', ' ');
            out().println("  " + st.bold(String.valueOf(r.get("caseId"))) + "  " + st.waiting(status) + "  alerts "
                    + String.join(", ", (List<String>) r.get("alerts")));
            AgentFinding f = facade().getCase(caseId).latestFinding();
            if (f == null) {
                out().println(st.field("Finding", "none yet: investigate one of its alerts"));
            } else {
                out().println(st.field("Finding", st.verdict(f.verdict()) + " " + f.confidence() + "  " + st.check(f.verification())
                        + "  " + f.verification().summary()));
                out().println();
                out().println(st.wrap(f.narrative(), 2));
                out().println();
            }
            ((List<?>) r.get("tasks")).forEach(t -> out().println(st.field("Task", String.valueOf(t))));
            ((List<tracewise.domain.CaseNote>) r.get("notes")).forEach(n -> out().println(st.field("Note",
                    Style.time(n.createdAt()) + "  " + n.author() + ": " + n.text())));
            out().println(st.label("  History") + st.dim("  (UTC)"));
            var rows = new java.util.ArrayList<List<String>>();
            for (AuditEntry e : (List<AuditEntry>) r.get("history")) {
                String details = e.details() == null ? "" : e.details().replaceAll("\\s+", " ").replace("**", "");
                rows.add(List.of(Style.time(e.timestamp()), e.actor().isHuman() ? e.actor().name() : e.actor().type().name(),
                        e.eventType().toLowerCase(java.util.Locale.ROOT).replace('_', ' '), e.target() == null ? "" : e.target(),
                        e.eventType().equals("AGENT_STEP") ? st.auditStep(details) : details));
            }
            st.table(List.of("TIME", "BY", "EVENT", "TARGET", "DETAIL"), "lllll", rows).lines()
                    .forEach(line -> out().println(st.cut(line)));
        }
    }

    @Command(name = "note", description = "Add a note to the case.")
    static class Note extends CliCommandBase {

        @Parameters(index = "0", description = "The case id.")
        String caseId;

        @Parameters(index = "1..*", arity = "1..*", description = "The note text.")
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
