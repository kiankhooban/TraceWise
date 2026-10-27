package tracewise.reporting;

import java.io.IOException;
import java.nio.file.Path;

/** Strategy: one exporter per file format; a PDF exporter would be one more implementation. */
public interface ReportExporter {

    ExportFormat format();

    /** Writes the report into dir and returns the file. */
    Path export(StrReport report, Path dir) throws IOException;
}
