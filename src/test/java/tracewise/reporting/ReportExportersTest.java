package tracewise.reporting;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.domain.PaymentChannel;
import tracewise.domain.Transaction;

class ReportExportersTest {

    @TempDir Path dir;

    static StrReport finalReport() {
        var t = new Transaction("TX-1", Instant.parse("2026-09-01T10:00:00Z"), "C1", "EXT-9", new BigDecimal("12000.50"),
                new BigDecimal("9000.00"), "USD", PaymentChannel.WIRE, "PA", true);
        var r = StrReport.draft("CAS-00001", "C1", List.of(t), "Wire to <script>alert(1)</script> & co.", "Escalated.", 1);
        r.lock(Instant.parse("2026-10-27T12:00:00Z"));
        return r;
    }

    @Test
    void jsonHasEveryFieldWithExactAmountsAndNoLabel() throws Exception {
        Path file = new JsonReportExporter().export(finalReport(), dir);

        assertEquals(dir.resolve("CAS-00001-STR.json"), file);
        String text = Files.readString(file);
        JsonNode json = tracewise.persistence.ExactJson.mapper().readTree(text);
        assertEquals("CAS-00001", json.get("case_id").asText());
        assertEquals("FINAL", json.get("status").asText());
        assertEquals("2026-10-27T12:00:00Z", json.get("finalised_at").asText());
        assertEquals("C1", json.get("subject_account").asText());
        JsonNode tx = json.get("transactions").get(0);
        assertEquals("TX-1", tx.get("id").asText());
        assertEquals("12000.50", tx.get("amount_cad").decimalValue().toPlainString());
        assertEquals("USD", tx.get("currency").asText());
        assertEquals("PA", tx.get("counterparty_country").asText());
        assertEquals("Wire to <script>alert(1)</script> & co.", json.get("grounds_for_suspicion").asText());
        assertEquals("Escalated.", json.get("actions_taken").asText());
        assertFalse(text.contains("label"), text);
        assertEquals(ExportFormat.JSON, new JsonReportExporter().format());
    }

    @Test
    void htmlIsPrintableAndEscapesTheText() throws Exception {
        Path file = new HtmlReportExporter().export(finalReport(), dir);

        assertEquals(dir.resolve("CAS-00001-STR.html"), file);
        String html = Files.readString(file);
        assertTrue(html.startsWith("<!DOCTYPE html>"));
        assertTrue(html.contains("Wire to &lt;script&gt;alert(1)&lt;/script&gt; &amp; co."), html);
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("TX-1") && html.contains("12000.50") && html.contains("EXT-9"));
        assertTrue(html.contains("Not submitted to FINTRAC"));
        assertFalse(html.toLowerCase().contains("label"));
        assertEquals(ExportFormat.HTML, new HtmlReportExporter().format());
    }
}
