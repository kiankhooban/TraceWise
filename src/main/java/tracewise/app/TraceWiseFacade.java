package tracewise.app;

import java.nio.file.Path;
import tracewise.ingest.ImportPreview;
import tracewise.ingest.ImportSummary;
import tracewise.persistence.Database;

/** Facade: the single entry point the GUI and the CLI use; one method per user operation. */
public class TraceWiseFacade implements AutoCloseable {

    private final Database database;
    private final ImportService importService;

    TraceWiseFacade(Database database, ImportService importService) {
        this.database = database;
        this.importService = importService;
    }

    public ImportPreview previewImport(Path file) {
        return importService.preview(file);
    }

    public ImportSummary importTransactions(Path file) {
        return importService.importFile(file);
    }

    @Override
    public void close() {
        database.close();
    }
}
