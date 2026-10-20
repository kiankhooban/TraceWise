package tracewise.app;

import java.util.List;
import tracewise.audit.AuditLog;
import tracewise.audit.SqliteAuditRepository;
import tracewise.domain.Actor;
import tracewise.ingest.CurrencyConverter;
import tracewise.ingest.IbmCsvFormat;
import tracewise.ingest.ScenarioCsvFormat;
import tracewise.monitoring.FanInRule;
import tracewise.monitoring.FanOutRule;
import tracewise.monitoring.InternationalTransferRule;
import tracewise.monitoring.LargeCashRule;
import tracewise.monitoring.PassThroughRule;
import tracewise.monitoring.RiskScorer;
import tracewise.monitoring.StructuringRule;
import tracewise.persistence.Database;
import tracewise.persistence.SqliteAlertRepository;
import tracewise.persistence.SqliteRuleConfigRepository;
import tracewise.persistence.SqliteThresholdReportRepository;
import tracewise.persistence.SqliteTransactionRepository;

/** Builds the object graph once (manual dependency injection), so no class creates its own dependencies. */
public final class TraceWiseBootstrap {

    private TraceWiseBootstrap() {
    }

    public static TraceWiseFacade create(AppConfig config) {
        var db = Database.open(config.databasePath());
        var transactions = new SqliteTransactionRepository(db);
        var audit = new AuditLog(new SqliteAuditRepository(db));
        var user = new Actor(config.userName(), config.userRole());
        var rates = CurrencyConverter.bankOfCanadaDefaults();
        var importService = new ImportService(transactions,
                List.of(new IbmCsvFormat(rates), new ScenarioCsvFormat(rates)), audit, user);
        var monitoringService = new MonitoringService(transactions,
                List.of(new LargeCashRule(), new InternationalTransferRule(), new StructuringRule(),
                        new PassThroughRule(), new FanInRule(), new FanOutRule()),
                new SqliteAlertRepository(db), new SqliteThresholdReportRepository(db),
                new SqliteRuleConfigRepository(db), new RiskScorer(), audit, user);
        return new TraceWiseFacade(db, importService, monitoringService);
    }
}
