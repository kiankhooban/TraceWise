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
        Style st = style();
        String under = " ".repeat(12); // the column the field values start in
        out().println(st.field("Imported", st.bold(s.rowsImported() + " of " + s.rowsRead() + " rows") + ", " + formatName));
        out().println(st.field("Accounts", String.valueOf(s.accounts())));
        if (s.earliest() != null) {
            out().println(st.field("Dates", Style.time(s.earliest()) + " to " + Style.time(s.latest())));
        }
        if (s.rejectedCount() > 0) {
            out().println(st.field("Rejected", st.bad(s.rejectedCount() + (s.rejectedCount() == 1 ? " row" : " rows"))));
            s.rejected().stream().limit(SHOWN).forEach(r -> out().println(under + "row " + r.rowNumber() + ": " + r.reason()));
        }
        if (s.rejectedCount() > SHOWN) {
            // a large file can reject hundreds of thousands of rows; they are counted by kind
            out().println(under + "... " + (s.rejectedCount() - SHOWN) + " more; all " + s.rejectedCount()
                    + " rejected rows by reason:");
            s.rejectedByReason().forEach((reason, n) -> out().println(under + "  " + reason + ": " + n));
        }
        if (s.rowsImported() > 0) {
            out().println(st.dim("  Next: monitor run"));
        }
    }
}
