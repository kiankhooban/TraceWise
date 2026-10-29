package tracewise.ingest;

/** The file cannot be imported at all: unreadable, or no supported format matches its header. */
public class ImportException extends RuntimeException {
    public ImportException(String message) {
        super(message);
    }
}
