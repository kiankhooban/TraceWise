package tracewise.ingest;

import java.util.List;

public record ImportPreview(String formatName, List<String> header, List<List<String>> firstRows) {
}
