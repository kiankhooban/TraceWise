package tracewise.app;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;
import tracewise.agent.AgentLimits;
import tracewise.agent.AgentRunner;
import tracewise.agent.tools.AccountHistoryTool;
import tracewise.agent.tools.CounterpartiesTool;
import tracewise.agent.tools.ToolRegistry;
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
import tracewise.llm.LLMClientFactory;
import tracewise.llm.ReplayStore;
import tracewise.persistence.Database;
import tracewise.persistence.SqliteAlertRepository;
import tracewise.persistence.SqliteCaseRepository;
import tracewise.persistence.SqliteRuleConfigRepository;
import tracewise.persistence.SqliteThresholdReportRepository;
import tracewise.persistence.SqliteTransactionRepository;
import tracewise.verification.ClaimVerifier;

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
        var alerts = new SqliteAlertRepository(db);
        var monitoringService = new MonitoringService(transactions,
                List.of(new LargeCashRule(), new InternationalTransferRule(), new StructuringRule(),
                        new PassThroughRule(), new FanInRule(), new FanOutRule()),
                alerts, new SqliteThresholdReportRepository(db),
                new SqliteRuleConfigRepository(db), new RiskScorer(), audit, user);

        var tools = new ToolRegistry();
        tools.register(new AccountHistoryTool(transactions));
        tools.register(new CounterpartiesTool(transactions));
        Supplier<AgentRunner> runner = once(() -> new AgentRunner(
                new LLMClientFactory(apiKey(config), new ReplayStore(config.replayDir())).create(config.model()),
                tools, AgentLimits.DEFAULT));
        var investigationService = new InvestigationService(db, alerts, new SqliteCaseRepository(db), runner,
                new ClaimVerifier(transactions), audit, user, config.model(), rates.toCad(BigDecimal.ONE, "USD"));
        return new TraceWiseFacade(db, importService, monitoringService, investigationService);
    }

    /** The key from .env, else from the environment; null when replaying, which needs none. */
    private static String apiKey(AppConfig config) {
        if (!"anthropic".equalsIgnoreCase(config.model().provider())) {
            return null;
        }
        String key = EnvFile.read(config.envFile()).get("ANTHROPIC_API_KEY");
        return key != null ? key : System.getenv("ANTHROPIC_API_KEY");
    }

    private static <T> Supplier<T> once(Supplier<T> create) {
        return new Supplier<>() {
            private T value;

            @Override
            public synchronized T get() {
                if (value == null) {
                    value = create.get();
                }
                return value;
            }
        };
    }
}
