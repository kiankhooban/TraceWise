package tracewise.ui.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import tracewise.actions.PendingAction;
import tracewise.reporting.ExportFormat;
import tracewise.reporting.JsonReportExporter;
import tracewise.reporting.StrReport;

@Command(name = "report", description = "Suspicious transaction reports: draft, edit, finalise and export (F11).",
        subcommands = {ReportCli.Show.class, ReportCli.Draft.class, ReportCli.Edit.class, ReportCli.Finalise.class,
                ReportCli.Export.class})
public class ReportCli {

    /** Prints a report the way show and draft return it. */
    abstract static class ReportCommand extends CliCommandBase {

        @Parameters(index = "0", description = "The case id, for example CAS-00001.")
        String caseId;

        Map<String, Object> shown(StrReport report) {
            return JsonReportExporter.toMap(report);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void print(Object result) {
            if (json || !(result instanceof Map)) {
                super.print(result);
                return;
            }
            var r = (Map<String, Object>) result;
            out().println("Report for " + r.get("case_id") + "  " + r.get("status")
                    + (r.get("finalised_at") == null ? "" : "  finalised " + r.get("finalised_at")));
            out().println("Subject account: " + r.get("subject_account"));
            out().println("Transactions:");
            for (Map<String, Object> t : (List<Map<String, Object>>) r.get("transactions")) {
                out().println("  " + t.get("id") + "  " + t.get("timestamp") + "  " + t.get("from_account") + " -> "
                        + t.get("to_account") + "  " + t.get("amount_cad") + " CAD  " + t.get("channel"));
            }
            out().println("Grounds for suspicion:\n  " + String.valueOf(r.get("grounds_for_suspicion")).replace("\n", "\n  "));
            out().println("Actions taken:\n  " + String.valueOf(r.get("actions_taken")).replace("\n", "\n  "));
        }
    }

    @Command(name = "show", description = "The case's report: subject, transactions, grounds and actions taken.")
    static class Show extends ReportCommand {

        @Override
        protected Object execute() {
            return shown(facade().getReport(caseId).orElseThrow(
                    () -> new IllegalStateException("Case " + caseId + " has no report yet: run report draft " + caseId)));
        }
    }

    @Command(name = "draft", description = "Draft the report: the model writes the grounds for suspicion from the "
            + "verified finding. An existing draft is replaced only with --replace.")
    static class Draft extends ReportCommand {

        @Option(names = "--replace", description = "Replace an existing draft, and the edits in it.")
        boolean replace;

        @Override
        protected Object execute() {
            return shown(facade().draftReport(caseId, replace));
        }
    }

    @Command(name = "edit", description = "Replace the grounds for suspicion or the actions taken.")
    static class Edit extends ReportCommand {

        @Option(names = "--grounds-file", description = "A text file with the new grounds for suspicion.")
        Path groundsFile;

        @Option(names = "--actions-file", description = "A text file with the new actions taken.")
        Path actionsFile;

        @Override
        protected Object execute() {
            if (groundsFile == null && actionsFile == null) {
                throw new IllegalArgumentException("Give --grounds-file, --actions-file or both");
            }
            StrReport current = facade().getReport(caseId).orElseThrow(
                    () -> new IllegalStateException("Case " + caseId + " has no report yet: run report draft " + caseId));
            return shown(facade().saveReport(caseId,
                    groundsFile == null ? current.groundsForSuspicion() : read(groundsFile),
                    actionsFile == null ? current.actionsTaken() : read(actionsFile)));
        }

        private static String read(Path file) {
            try {
                return Files.readString(file).strip();
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot read " + file + ": " + e.getMessage());
            }
        }
    }

    @Command(name = "finalise", description = "Ask for the report to be finalised; it waits in the approval queue.")
    static class Finalise extends ReportCommand {

        @Override
        protected Object execute() {
            PendingAction p = facade().requestFinalisation(caseId);
            return "Finalisation of the report for " + caseId + " is waiting for approval as " + p.id()
                    + ". Approve it with: tracewise approvals approve " + p.id();
        }
    }

    @Command(name = "export", description = "Write a final report as HTML, JSON or both. Nothing is sent to FINTRAC.")
    static class Export extends ReportCommand {

        @Option(names = "--format", split = ",", description = "html, json or both (default: both).")
        List<String> formats;

        @Option(names = "--out", defaultValue = ".", description = "The folder to write to (default: ${DEFAULT-VALUE}).")
        Path out;

        @Override
        protected Object execute() {
            var chosen = EnumSet.noneOf(ExportFormat.class);
            if (formats == null) {
                chosen = EnumSet.allOf(ExportFormat.class);
            } else {
                for (String f : formats) {
                    if (f.strip().equalsIgnoreCase("both")) {
                        chosen.addAll(EnumSet.allOf(ExportFormat.class));
                        continue;
                    }
                    try {
                        chosen.add(ExportFormat.valueOf(f.strip().toUpperCase(java.util.Locale.ROOT)));
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Unknown format " + f + ": use html, json or both");
                    }
                }
            }
            return facade().exportReport(caseId, chosen, out).stream().map(Path::toString).toList();
        }
    }
}
