package tracewise.reporting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import tracewise.domain.Transaction;
import tracewise.persistence.ExactJson;

/** The report as JSON for other systems; amounts keep their exact decimals. */
public class JsonReportExporter implements ReportExporter {

    @Override
    public ExportFormat format() {
        return ExportFormat.JSON;
    }

    @Override
    public Path export(StrReport report, Path dir) throws IOException {
        var body = new LinkedHashMap<String, Object>();
        body.put("case_id", report.caseId());
        body.put("status", report.status().name());
        body.put("finalised_at", report.finalisedAt() == null ? null : report.finalisedAt().toString());
        body.put("subject_account", report.subjectAccountId());
        body.put("transactions", report.transactions().stream().map(JsonReportExporter::row).toList());
        body.put("grounds_for_suspicion", report.groundsForSuspicion());
        body.put("actions_taken", report.actionsTaken());
        body.put("note", HtmlReportExporter.NOT_SUBMITTED);
        Path file = dir.resolve(report.caseId() + "-STR.json");
        Files.writeString(file, ExactJson.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(body));
        return file;
    }

    /** Named fields only: the ground-truth label in the data set never leaves the database. */
    private static Map<String, Object> row(Transaction t) {
        var row = new LinkedHashMap<String, Object>();
        row.put("id", t.id());
        row.put("timestamp", t.timestamp().toString());
        row.put("from_account", t.senderAccountId());
        row.put("to_account", t.receiverAccountId());
        row.put("amount_cad", t.amountCad());
        row.put("original_amount", t.originalAmount());
        row.put("currency", t.currency());
        row.put("channel", t.channel().name());
        row.put("counterparty_country", t.counterpartyCountry());
        return row;
    }
}
