package tracewise.app;

import java.util.List;
import tracewise.audit.AuditLog;
import tracewise.audit.SqliteAuditRepository;
import tracewise.domain.Actor;
import tracewise.domain.ActorType;
import tracewise.ingest.CurrencyConverter;
import tracewise.ingest.IbmCsvFormat;
import tracewise.ingest.ScenarioCsvFormat;
import tracewise.persistence.Database;
import tracewise.persistence.SqliteTransactionRepository;

/** Builds the object graph once (manual dependency injection), so no class creates its own dependencies. */
public final class TraceWiseBootstrap {

    private TraceWiseBootstrap() {
    }

    public static TraceWiseFacade create(AppConfig config) {
        var db = Database.open(config.databasePath());
        var transactions = new SqliteTransactionRepository(db);
        var audit = new AuditLog(new SqliteAuditRepository(db));
        var analyst = new Actor(config.analystName(), ActorType.ANALYST);
        var rates = CurrencyConverter.bankOfCanadaDefaults();
        var importService = new ImportService(transactions,
                List.of(new IbmCsvFormat(rates), new ScenarioCsvFormat(rates)), audit, analyst);
        return new TraceWiseFacade(db, importService);
    }
}
