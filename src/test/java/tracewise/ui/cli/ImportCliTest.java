package tracewise.ui.cli;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImportCliTest {

    @TempDir Path dir;
    final StringWriter out = new StringWriter();
    final StringWriter err = new StringWriter();

    int run(String... args) {
        var all = new java.util.ArrayList<>(List.of("--db", dir.resolve("t.db").toString()));
        all.addAll(List.of(args));
        return TraceWiseCli.commandLine(new PrintWriter(out, true), new PrintWriter(err, true))
                .execute(all.toArray(String[]::new));
    }

    Path csv(String... lines) throws IOException {
        return Files.write(dir.resolve("s.csv"), List.of(lines));
    }

    static final String HEADER = "transaction_id,timestamp,sender_bank,sender_account,receiver_bank,"
            + "receiver_account,amount,currency,channel,counterparty_country,is_laundering";

    @Test
    void importPrintsAReadableSummary() throws IOException {
        var file = csv(HEADER, "T1,2026-09-01T10:00:00,,CASH,001,C1,9500,CAD,CASH,CA,",
                "T2,2026-09-01T11:00:00,001,C1,001,C2,-1,CAD,WIRE,CA,");
        assertEquals(0, run("import", file.toString()));
        String text = out.toString();
        assertTrue(text.contains("Format: TraceWise scenario CSV"), text);
        assertTrue(text.contains("Imported 1 of 2 rows"), text);
        assertTrue(text.contains("row 3: amount must be positive"), text);
    }

    @Test
    void jsonModePrintsTheSummaryAsJson() throws IOException {
        var file = csv(HEADER, "T1,2026-09-01T10:00:00,,CASH,001,C1,9500,CAD,CASH,CA,");
        assertEquals(0, run("import", "--json", file.toString()));
        var json = new ObjectMapper().readTree(out.toString());
        assertEquals(1, json.get("rowsImported").asInt());
        assertEquals("2026-09-01T10:00:00Z", json.get("earliest").asText());
    }

    @Test
    void failuresExitWithOneAndExplainWhy() throws IOException {
        var file = csv("date,amount", "2026-09-01,5");
        assertEquals(1, run("import", file.toString()));
        assertTrue(err.toString().startsWith("Error: unrecognised file format"), err.toString());

        out.getBuffer().setLength(0);
        assertEquals(1, run("import", "--json", file.toString()));
        assertTrue(new ObjectMapper().readTree(out.toString()).get("error").asText().startsWith("unrecognised"));
    }

    /** Many rejected rows are counted by reason after the first 20, so a large file's output stays readable. */
    @Test
    void manyRejectedRowsAreSummarisedByReason() throws IOException {
        var lines = new java.util.ArrayList<String>(List.of(HEADER));
        for (int i = 1; i <= 30; i++) {
            lines.add("T" + i + ",2026-09-01T10:00:00,,C1,001,C2,100," + (i % 2 == 0 ? "XXX" : "YYY") + ",WIRE,CA,");
        }
        assertEquals(0, run("import", Files.write(dir.resolve("r.csv"), lines).toString()));

        String text = out.toString();
        assertEquals(20, text.split("rejected row ", -1).length - 1, text);
        assertTrue(text.contains("10 more rejected rows"), text);
        assertTrue(text.contains("currency not in rate table: XXX: 5") && text.contains("currency not in rate table: YYY: 5"),
                text);
    }
}

