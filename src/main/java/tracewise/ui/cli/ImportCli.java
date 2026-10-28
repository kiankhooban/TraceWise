package tracewise.ui.cli;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import tracewise.ingest.ImportSummary;
import tracewise.ingest.RejectedRow;

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
        List<RejectedRow> rejected = s.rejected();
        rejected.stream().limit(SHOWN).forEach(r -> out().println("  rejected row " + r.rowNumber() + ": " + r.reason()));
        if (rejected.size() > SHOWN) {
            // a large file can reject hundreds of thousands of rows; --json lists every one
            out().println("  ... " + (rejected.size() - SHOWN) + " more rejected rows, by reason:");
            rejected.subList(SHOWN, rejected.size()).stream()
                    .collect(Collectors.groupingBy(RejectedRow::reason, LinkedHashMap::new, Collectors.counting()))
                    .forEach((reason, n) -> out().println("    " + reason + ": " + n));
        }
    }
}
