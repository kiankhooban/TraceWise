package tracewise.retrieval;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** FINTRAC's general money-laundering indicators, read from the copy shipped with the application. */
public final class IndicatorCorpus {

    private IndicatorCorpus() {
    }

    public static List<Passage> load() {
        return load("/tracewise/fintrac-indicators.tsv");
    }

    /** Lines "id TAB section TAB text"; lines starting with # are comments. */
    static List<Passage> load(String resource) {
        InputStream in = IndicatorCorpus.class.getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Indicator retrieval is unavailable: " + resource + " is missing");
        }
        var passages = new ArrayList<Passage>();
        try (var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.split("\t");
                if (f.length != 3) {
                    throw new IllegalStateException("Indicator retrieval is unavailable: bad line in " + resource);
                }
                passages.add(new Passage("FINTRAC " + f[0] + ", " + f[1], f[2]));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Indicator retrieval is unavailable: " + e.getMessage(), e);
        }
        return passages;
    }
}
