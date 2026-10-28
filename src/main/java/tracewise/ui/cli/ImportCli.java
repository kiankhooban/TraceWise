package tracewise.ui.cli;

import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import tracewise.ingest.ImportSummary;

@Command(name = "import", description = "Import transactions from a CSV file (IBM AML or TraceWise scenario format).")
public class ImportCli extends CliCommandBase {

    @Parameters(paramLabel = "FILE", description = "The CSV file to import.")
    Path file;

    private static final int SHOWN = 20;

    private String formatName;

    @Override
    protected Object execute() {
        formatName = facade().previewImport(file).formatName();
        return facade().importTransactions(file);
    }

    @Override
    protected void print(Object result) {
        if (json) {
            super.print(result);
            return;
        }
        var s = (ImportSummary) result;
        out().println("Format: " + formatName);
        out().printf("Imported %d of %d rows; %d accounts%n", s.rowsImported(), s.rowsRead(), s.accounts());
        if (s.earliest() != null) {
            out().println("Date range: " + s.earliest() + " to " + s.latest());
        }
        s.rejected().stream().limit(SHOWN).forEach(r -> out().println("  rejected row " + r.rowNumber() + ": " + r.reason()));
        if (s.rejectedCount() > SHOWN) {
            // a large file can reject hundreds of thousands of rows; they are counted by kind
            out().println("  ... " + (s.rejectedCount() - SHOWN) + " more; all " + s.rejectedCount()
                    + " rejected rows by reason:");
            s.rejectedByReason().forEach((reason, n) -> out().println("    " + reason + ": " + n));
        }
    }
}
