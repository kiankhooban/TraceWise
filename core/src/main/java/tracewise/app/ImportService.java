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
import java.util.Set;
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
import tracewise.persistence.Database;
import tracewise.persistence.TransactionRepository;

/** F01: detects the file's format, validates every row, and stores the valid ones in one transaction. */
public class ImportService {

    private static final int PREVIEW_ROWS = 10;
    /** Rows saved at a time, so a large file is never held in memory whole. */
    static final int BATCH_ROWS = 10_000;
    static final int REJECTED_KEPT = 1_000;

    private final Database db;
    private final int batchRows;
    private final TransactionRepository transactions;
    private final List<TransactionFormat> formats;
    private final AuditLog audit;
    private final Actor actor;

    public ImportService(Database db, TransactionRepository transactions, List<TransactionFormat> formats,
            AuditLog audit, Actor actor) {
        this(db, transactions, formats, audit, actor, BATCH_ROWS);
    }

    ImportService(Database db, TransactionRepository transactions, List<TransactionFormat> formats, AuditLog audit,
            Actor actor, int batchRows) {
        this.db = db;
        this.batchRows = batchRows;
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

    /**
     * Reads the file once, saving valid rows in chunks of batchRows inside one database transaction: a large file is
     * never held in memory whole, and a failure still leaves nothing behind.
     */
    public ImportSummary importFile(Path file) {
        String target = file.getFileName().toString();
        var progress = new Progress();
        try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String header = in.readLine();
            if (header == null) {
                throw new ImportException("the file is empty");
            }
            TransactionFormat format = detect(split(header.replace("\uFEFF", "")));
            try {
                db.inTransaction(() -> readRows(in, file, format, progress));
            } catch (RuntimeException | Error e) {
                audit.append(new AuditEntry(Instant.now(), actor, "IMPORT", target, String.valueOf(e), "FAILED"));
                throw e;
            }
        } catch (IOException e) {
            throw new ImportException("cannot read file: " + file);
        }
        var summary = new ImportSummary(progress.rowsRead, progress.imported, List.copyOf(progress.rejected),
                progress.rejectedCount, java.util.Collections.unmodifiableMap(progress.byReason), progress.accounts.size(),
                progress.earliest, progress.latest);
        audit.append(new AuditEntry(Instant.now(), actor, "IMPORT", target, summary.rowsImported() + " of "
                + summary.rowsRead() + " rows imported, " + summary.rejectedCount() + " rejected", "OK"));
        return summary;
    }

    private void readRows(BufferedReader in, Path file, TransactionFormat format, Progress p) {
        var batch = new ArrayList<Transaction>();
        var batchIds = new HashSet<String>(); // earlier chunks are already in the database, where exists() finds them
        int rowNumber = 1;
        try {
            String line;
            while ((line = in.readLine()) != null) {
                rowNumber++;
                List<String> row = split(line);
                if (row.size() == 1 && row.get(0).isBlank()) {
                    continue;
                }
                p.rowsRead++;
                try {
                    Transaction t = format.parse(row, rowNumber);
                    if (!batchIds.add(t.id()) || transactions.exists(t.id())) {
                        p.reject(new RejectedRow(rowNumber, "duplicate transaction id: " + t.id()));
                    } else {
                        batch.add(t);
                        p.count(t);
                    }
                } catch (IllegalArgumentException e) {
                    p.reject(new RejectedRow(rowNumber, e.getMessage()));
                }
                if (batch.size() >= batchRows) {
                    transactions.saveAll(batch);
                    batch.clear();
                    batchIds.clear();
                }
            }
        } catch (IOException e) {
            throw new ImportException("cannot read file: " + file);
        }
        if (!batch.isEmpty()) {
            transactions.saveAll(batch);
        }
    }

    /** What the import has seen so far. */
    private static final class Progress {
        int rowsRead;
        int imported;
        /** The first REJECTED_KEPT rejected rows; all of them are counted by kind. */
        final List<RejectedRow> rejected = new ArrayList<>();
        final java.util.Map<String, Integer> byReason = new java.util.LinkedHashMap<>();
        int rejectedCount;
        final Set<String> accounts = new HashSet<>();
        Instant earliest;
        Instant latest;

        void reject(RejectedRow row) {
            rejectedCount++;
            if (rejected.size() < REJECTED_KEPT) {
                rejected.add(row);
            }
            byReason.merge(row.kind(), 1, Integer::sum);
        }

        void count(Transaction t) {
            imported++;
            for (String id : List.of(t.senderAccountId(), t.receiverAccountId())) {
                if (!Transaction.isOutsideBank(id)) {
                    accounts.add(id);
                }
            }
            earliest = earliest == null || t.timestamp().isBefore(earliest) ? t.timestamp() : earliest;
            latest = latest == null || t.timestamp().isAfter(latest) ? t.timestamp() : latest;
        }
    }

    private TransactionFormat detect(List<String> header) {
        return formats.stream().filter(f -> f.matches(header)).findFirst().orElseThrow(() -> new ImportException(
                "unrecognised file format; the header must be one of: " + formats.stream()
                        .map(f -> f.name() + " (" + String.join(",", f.header()) + ")")
                        .collect(Collectors.joining("; "))));
    }

    private static List<String> split(String line) {
        return Arrays.asList(line.split(",", -1));
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
                rows.add(split(line));
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
