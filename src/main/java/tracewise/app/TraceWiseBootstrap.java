package tracewise.app;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import tracewise.retrieval.EmbeddingRetriever;
import tracewise.retrieval.IndicatorCorpus;
import tracewise.retrieval.CaseMemory;
import tracewise.retrieval.Passage;
import tracewise.retrieval.Retriever;
import tracewise.agent.AgentLimits;
import tracewise.agent.AgentRunner;
import tracewise.actions.ApprovalQueue;
import tracewise.actions.CommandContext;
import tracewise.actions.CommandDispatcher;
import tracewise.actions.PermissionPolicy;
import tracewise.agent.tools.AccountHistoryTool;
import tracewise.agent.tools.ActionScope;
import tracewise.agent.tools.AddToWatchlistTool;
import tracewise.agent.tools.CounterpartiesTool;
import tracewise.agent.tools.CreateTaskTool;
import tracewise.agent.tools.GetCaseTool;
import tracewise.agent.tools.LinkAlertsTool;
import tracewise.agent.tools.LinkedAlertsTool;
import tracewise.agent.tools.ProposeCloseTool;
import tracewise.agent.tools.ProposeEscalationTool;
import tracewise.agent.tools.QueryAlertsTool;
import tracewise.agent.tools.QueryCasesTool;
import tracewise.agent.tools.SearchIndicatorsTool;
import tracewise.agent.tools.SetPriorityTool;
import tracewise.agent.tools.SimilarCasesTool;
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
import tracewise.llm.LLMClient;
import tracewise.llm.LLMClientFactory;
import tracewise.llm.ReplayStore;
import tracewise.persistence.AlertRepository;
import tracewise.persistence.CaseRepository;
import tracewise.persistence.Database;
import tracewise.persistence.TransactionRepository;
import tracewise.persistence.SqliteAlertRepository;
import tracewise.persistence.SqliteApprovalRepository;
import tracewise.persistence.SqliteCaseRepository;
import tracewise.persistence.SqliteRuleConfigRepository;
import tracewise.persistence.SqliteThresholdReportRepository;
import tracewise.persistence.SqliteTransactionRepository;
import tracewise.reporting.HtmlReportExporter;
import tracewise.reporting.JsonReportExporter;
import tracewise.verification.ClaimVerifier;

/** Builds the object graph once (manual dependency injection), so no class creates its own dependencies. */
public final class TraceWiseBootstrap {

    private TraceWiseBootstrap() {
    }

    public static TraceWiseFacade create(AppConfig config) {
        return create(config, () -> new LLMClientFactory(apiKey(config), replayStore(config.replayDir()))
                .create(config.model()));
    }

    /** llm is asked for the model client only when an investigation first needs it. */
    public static TraceWiseFacade create(AppConfig config, Supplier<LLMClient> llm) {
        var db = Database.open(config.databasePath());
        var transactions = new SqliteTransactionRepository(db);
        var audit = new AuditLog(new SqliteAuditRepository(db));
        var user = new Actor(config.userName(), config.userRole());
        var rates = CurrencyConverter.bankOfCanadaDefaults();
        var importService = new ImportService(db, transactions,
                List.of(new IbmCsvFormat(rates), new ScenarioCsvFormat(rates)), audit, user);
        var alerts = new SqliteAlertRepository(db);
        var monitoringService = new MonitoringService(transactions,
                List.of(new LargeCashRule(), new InternationalTransferRule(), new StructuringRule(),
                        new PassThroughRule(), new FanInRule(), new FanOutRule()),
                alerts, new SqliteThresholdReportRepository(db),
                new SqliteRuleConfigRepository(db), new RiskScorer(), audit, user);

        var cases = new SqliteCaseRepository(db);
        // the local embedding model takes about 2 s to load, so commands that never retrieve do not pay for it
        Supplier<EmbeddingModel> embeddings = once(BgeSmallEnV15QuantizedEmbeddingModel::new);
        var indicators = indicatorRetriever(IndicatorCorpus::load, embeddings);
        var memory = new CaseMemory(cases, () -> new EmbeddingRetriever(embeddings.get(), EmbeddingRetriever.MIN_SCORE));
        var commands = new CommandContext(cases, alerts, transactions, memory);
        var approvals = new ApprovalQueue(db, new SqliteApprovalRepository(db), commands, audit);
        var dispatcher = new CommandDispatcher(db, commands, new PermissionPolicy(), approvals, audit);
        var scope = new ActionScope(cases);
        var tools = investigationTools(transactions, alerts, cases, indicators, memory, dispatcher, scope);
        Supplier<LLMClient> model = once(llm);
        Supplier<AgentRunner> runner = once(() -> new AgentRunner(model.get(), tools, AgentLimits.DEFAULT));
        var verifier = new ClaimVerifier(transactions);
        BigDecimal cadPerUsd = rates.toCad(BigDecimal.ONE, "USD");
        var investigationService = new InvestigationService(db, alerts, cases, runner, scope, approvals, verifier, audit,
                user, config.model(), cadPerUsd);
        var assistant = new AssistantService(runner, verifier, audit, user, config.model(), cadPerUsd);
        var triageService = new TriageService(alerts, cases, investigationService, audit, user);
        return new TraceWiseFacade(db, importService, monitoringService, investigationService, triageService, approvals,
                new CaseService(cases, dispatcher, approvals, audit, user), assistant, new ReportService(db, cases, alerts, transactions, model, dispatcher,
                approvals, audit, List.of(new HtmlReportExporter(), new JsonReportExporter()), user, config.model(),
                cadPerUsd), audit, indicators, memory, user);
    }

    /**
     * Recordings of model answers. Retrieval results are left out of matching: the local embedding model's
     * scores differ between ARM and x86 CPUs, and similar cases change as cases are decided.
     */
    public static ReplayStore replayStore(java.nio.file.Path dir) {
        return new ReplayStore(dir, java.util.Set.of(SearchIndicatorsTool.NAME, SimilarCasesTool.NAME));
    }

    /** The key from .env, else from the environment; null when replaying, which needs none. */
    private static String apiKey(AppConfig config) {
        if (!"anthropic".equalsIgnoreCase(config.model().provider())) {
            return null;
        }
        String key = EnvFile.read(config.envFile()).get("ANTHROPIC_API_KEY");
        return key != null ? key : System.getenv("ANTHROPIC_API_KEY");
    }

    /**
     * Every tool an agent task may call: read tools, then action tools that go through the dispatcher. Each task
     * offers its own subset (the runner refuses the rest).
     */
    public static ToolRegistry investigationTools(TransactionRepository transactions, AlertRepository alerts,
            CaseRepository cases, Retriever indicators, CaseMemory memory, CommandDispatcher dispatcher,
            ActionScope scope) {
        var tools = new ToolRegistry();
        tools.register(new AccountHistoryTool(transactions));
        tools.register(new CounterpartiesTool(transactions));
        tools.register(new LinkedAlertsTool(transactions, alerts, cases));
        tools.register(new SearchIndicatorsTool(indicators));
        tools.register(new SimilarCasesTool(memory));
        tools.register(new QueryCasesTool(cases, alerts));
        tools.register(new QueryAlertsTool(alerts, cases));
        tools.register(new GetCaseTool(cases, alerts));
        tools.register(new SetPriorityTool(dispatcher, scope));
        tools.register(new LinkAlertsTool(dispatcher, scope));
        tools.register(new AddToWatchlistTool(dispatcher, scope));
        tools.register(new CreateTaskTool(dispatcher, scope));
        tools.register(new ProposeCloseTool(dispatcher, scope));
        tools.register(new ProposeEscalationTool(dispatcher, scope));
        return tools;
    }

    /**
     * The indicator index, built on the first search. If the corpus cannot be read, every search fails with
     * that limitation, which the agent sees as a tool error and the trace records; investigations go on.
     */
    static Retriever indicatorRetriever(Supplier<List<Passage>> corpus, Supplier<EmbeddingModel> model) {
        Supplier<EmbeddingRetriever> index = once(() -> {
            var passages = corpus.get();
            var retriever = new EmbeddingRetriever(model.get(), EmbeddingRetriever.MIN_SCORE);
            retriever.index(passages);
            return retriever;
        });
        return (query, maxResults) -> index.get().search(query, maxResults);
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
