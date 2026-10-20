package tracewise.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import tracewise.audit.AuditEntry;
import tracewise.audit.AuditLog;
import tracewise.domain.Actor;
import tracewise.domain.Transaction;
import tracewise.ingest.ImportException;
import tracewise.ingest.ImportPreview;
import tracewise.ingest.ImportSummary;
import tracewise.ingest.RejectedRow;
import tracewise.ingest.TransactionFormat;
import tracewise.persistence.TransactionRepository;

/** F01: detects the file's format, validates every row, and stores the valid ones in one transaction. */
public class ImportService {

    private static final int PREVIEW_ROWS = 10;

    private final TransactionRepository transactions;
    private final List<TransactionFormat> formats;
    private final AuditLog audit;
    private final Actor actor;

    public ImportService(TransactionRepository transactions, List<TransactionFormat> formats, AuditLog audit,
            Actor actor) {
        this.transactions = transactions;
        this.formats = List.copyOf(formats);
        this.audit = audit;
        this.actor = actor;
    }

    public ImportPreview preview(Path file) {
        List<List<String>> rows = read(file, PREVIEW_ROWS + 1);
        TransactionFormat format = detect(rows.get(0));
        return new ImportPreview(format.name(), rows.get(0), rows.subList(1, rows.size()));
    }

    public ImportSummary importFile(Path file) {
        List<List<String>> rows = read(file, Integer.MAX_VALUE);
        TransactionFormat format = detect(rows.get(0));

        var valid = new ArrayList<Transaction>();
        var rejected = new ArrayList<RejectedRow>();
        var seenIds = new HashSet<String>();
        int rowsRead = 0;
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.size() == 1 && row.get(0).isBlank()) {
                continue;
            }
            rowsRead++;
            int rowNumber = i + 1;
            try {
                Transaction t = format.parse(row, rowNumber);
                if (!seenIds.add(t.id()) || transactions.exists(t.id())) {
                    rejected.add(new RejectedRow(rowNumber, "duplicate transaction id: " + t.id()));
                } else {
                    valid.add(t);
                }
            } catch (IllegalArgumentException e) {
                rejected.add(new RejectedRow(rowNumber, e.getMessage()));
            }
        }

        String target = file.getFileName().toString();
        try {
            transactions.saveAll(valid);
        } catch (RuntimeException e) {
            audit.append(new AuditEntry(Instant.now(), actor, "IMPORT", target, e.getMessage(), "FAILED"));
            throw e;
        }
        var summary = summarise(rowsRead, valid, rejected);
        audit.append(new AuditEntry(Instant.now(), actor, "IMPORT", target, summary.rowsImported() + " of "
                + rowsRead + " rows imported, " + rejected.size() + " rejected", "OK"));
        return summary;
    }

    private static ImportSummary summarise(int rowsRead, List<Transaction> valid, List<RejectedRow> rejected) {
        var accounts = new HashSet<String>();
        for (Transaction t : valid) {
            accounts.add(t.senderAccountId());
            accounts.add(t.receiverAccountId());
        }
        accounts.remove(Transaction.CASH);
        Instant earliest = valid.stream().map(Transaction::timestamp).min(Instant::compareTo).orElse(null);
        Instant latest = valid.stream().map(Transaction::timestamp).max(Instant::compareTo).orElse(null);
        return new ImportSummary(rowsRead, valid.size(), List.copyOf(rejected), accounts.size(), earliest, latest);
    }

    private TransactionFormat detect(List<String> header) {
        return formats.stream().filter(f -> f.matches(header)).findFirst().orElseThrow(() -> new ImportException(
                "unrecognised file format; the header must be one of: " + formats.stream()
                        .map(f -> f.name() + " (" + String.join(",", f.header()) + ")")
                        .collect(Collectors.joining("; "))));
    }

    // shortcut: plain comma split, no quoted fields; both supported formats have none. Upgrade if a format needs quotes.
    private static List<List<String>> read(Path file, int maxRows) {
        try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            var rows = new ArrayList<List<String>>();
            String line;
            while (rows.size() < maxRows && (line = in.readLine()) != null) {
                if (rows.isEmpty()) {
                    line = line.replace("﻿", "");
                }
                rows.add(Arrays.asList(line.split(",", -1)));
            }
            if (rows.isEmpty()) {
                throw new ImportException("the file is empty");
            }
            return rows;
        } catch (IOException e) {
            throw new ImportException("cannot read file: " + file);
        }
    }
}
