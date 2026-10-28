package tracewise.app;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import tracewise.actions.ApprovalQueue;
import tracewise.actions.PendingAction;
import tracewise.agent.AgentEventListener;
import tracewise.agent.AssistantAnswer;
import tracewise.agent.AssistantContext;
import tracewise.agent.InvestigationResult;
import tracewise.agent.TriageReport;
import tracewise.audit.AuditEntry;
import tracewise.audit.AuditFilter;
import tracewise.audit.AuditLog;
import tracewise.domain.Actor;
import tracewise.domain.Alert;
import tracewise.domain.Case;
import tracewise.domain.ThresholdReport;
import tracewise.ingest.ImportPreview;
import tracewise.ingest.ImportSummary;
import tracewise.monitoring.AlertFilter;
import tracewise.monitoring.DetectionRule;
import tracewise.monitoring.MonitoringSummary;
import tracewise.monitoring.RuleConfig;
import tracewise.persistence.Database;
import tracewise.persistence.TransactionRepository;
import tracewise.network.MoneyFlowGraph;
import tracewise.domain.DateRange;
import tracewise.domain.Transaction;
import tracewise.reporting.ExportFormat;
import tracewise.reporting.StrReport;
import tracewise.retrieval.CaseMemory;
import tracewise.retrieval.RetrievedPassage;
import tracewise.retrieval.Retriever;

/** Facade: the single entry point the GUI and the CLI use; one method per user operation. */
public class TraceWiseFacade implements AutoCloseable {

    private final Database database;
    private final ImportService importService;
    private final MonitoringService monitoringService;
    private final InvestigationService investigationService;
    private final TriageService triageService;
    private final ApprovalQueue approvals;
    private final CaseService caseService;
    private final AssistantService assistant;
    private final ReportService reports;
    private final NetworkService network;
    private final TransactionRepository transactions;
    private final AuditLog audit;
    private final Retriever knowledge;
    private final CaseMemory memory;
    private final Actor user;

    TraceWiseFacade(Database database, ImportService importService, MonitoringService monitoringService,
            InvestigationService investigationService, TriageService triageService, ApprovalQueue approvals,
            CaseService caseService, AssistantService assistant, ReportService reports, NetworkService network,
            TransactionRepository transactions, AuditLog audit, Retriever knowledge, CaseMemory memory,
            Actor user) {
        this.database = database;
        this.importService = importService;
        this.monitoringService = monitoringService;
        this.investigationService = investigationService;
        this.triageService = triageService;
        this.approvals = approvals;
        this.caseService = caseService;
        this.assistant = assistant;
        this.reports = reports;
        this.network = network;
        this.transactions = transactions;
        this.audit = audit;
        this.knowledge = knowledge;
        this.memory = memory;
        this.user = user;
    }

    public ImportPreview previewImport(Path file) {
        return importService.preview(file);
    }

    public ImportSummary importTransactions(Path file) {
        return importService.importFile(file);
    }

    public MonitoringSummary runMonitoring() {
        return monitoringService.run();
    }

    public List<Alert> listAlerts(AlertFilter filter) {
        return monitoringService.listAlerts(filter);
    }

    public List<ThresholdReport> listThresholdReports() {
        return monitoringService.listThresholdReports();
    }

    public List<DetectionRule> listRules() {
        return monitoringService.listRules();
    }

    public void updateRule(String ruleId, RuleConfig config) {
        monitoringService.updateRule(ruleId, config);
    }

    /** Runs the agent on the alert; listener (may be null) sees each step as it happens. */
    public InvestigationResult investigate(String alertId, AgentEventListener listener) {
        return investigationService.investigate(alertId, listener);
    }

    public void cancelInvestigation(String alertId) {
        investigationService.cancel(alertId);
    }

    /** Investigates the open alerts in risk order within the limits; listener (may be null) sees each step. */
    public TriageReport triage(TriageLimits limits, AgentEventListener listener) {
        return triageService.triage(limits, listener);
    }

    public void stopTriage() {
        triageService.stop();
    }

    public List<PendingAction> pendingApprovals() {
        return approvals.pending();
    }

    /** The session's analyst or supervisor approves; the proposal runs, or expires if the case changed. */
    public PendingAction approve(String pendingId) {
        approvals.approve(pendingId, user);
        return approvals.find(pendingId);
    }

    public PendingAction reject(String pendingId, String reason) {
        approvals.reject(pendingId, user, reason);
        return approvals.find(pendingId);
    }

    /** F08: the audit trail in the order it happened, filtered by event type and target. */
    public List<AuditEntry> queryAudit(AuditFilter filter) {
        return audit.query(filter);
    }

    /** F08: the session's user adds a note to a case. */
    public void addNote(String caseId, String text) {
        caseService.addNote(caseId, text);
    }

    /** F08: the session's user closes a case as a false alarm, giving the reason. */
    public void closeCase(String caseId, String reason) {
        caseService.close(caseId, reason);
    }

    /** F08: the session's user returns a closed case to Open, giving the reason. */
    public void reopenCase(String caseId, String reason) {
        caseService.reopen(caseId, reason);
    }

    /** F08: the matching audit entries as CSV; returns how many were written. */
    public int exportAudit(AuditFilter filter, java.io.Writer out) {
        List<AuditEntry> entries = audit.query(filter);
        try {
            tracewise.audit.AuditCsv.write(entries, out);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("The audit export could not be written: " + e.getMessage(), e);
        }
        return entries.size();
    }

    /** F08: the case's history timeline. */
    public List<AuditEntry> caseHistory(String caseId) {
        return caseService.history(caseId);
    }

    /** The case an alert belongs to (an alert linked into another case belongs to that case), if any. */
    public java.util.Optional<String> caseOfAlert(String alertId) {
        return caseService.caseOfAlert(alertId);
    }

    public List<Case> listCases() {
        return caseService.listCases();
    }

    public Case getCase(String caseId) {
        return caseService.getCase(caseId);
    }

    /** The analyst's search (UC16): FINTRAC indicators and past cases matching the text, best first. */
    public List<RetrievedPassage> searchKnowledge(String query) {
        return Stream.concat(knowledge.search(query, 5).stream(), memory.similar(query, 5).stream())
                .sorted(Comparator.comparingDouble(RetrievedPassage::score).reversed()).toList();
    }

    /** Closed and escalated cases matching the indicators of the case's latest finding, other than the case itself. */
    public List<RetrievedPassage> similarCases(String caseId) {
        var finding = getCase(caseId).latestFinding();
        if (finding == null) {
            throw new IllegalArgumentException("Case " + caseId + " has no finding yet, so there is nothing to compare");
        }
        return memory.similar(CaseMemory.pattern(finding), 6).stream()
                .filter(p -> !p.source().equals(caseId)).limit(5).toList();
    }

    /** F10: a free-text question, optionally about the case or account the analyst is looking at. */
    public AssistantAnswer ask(String question, AssistantContext context) {
        return assistant.ask(question, context);
    }

    /** F11: the case's report, if one has been drafted. */
    public java.util.Optional<StrReport> getReport(String caseId) {
        return reports.report(caseId);
    }

    /** F11: drafts the report; replace must be true to write over an existing draft. The model writes the grounds. */
    public StrReport draftReport(String caseId, boolean replace) {
        return reports.draft(caseId, replace);
    }

    public StrReport saveReport(String caseId, String groundsForSuspicion, String actionsTaken) {
        return reports.save(caseId, groundsForSuspicion, actionsTaken);
    }

    /** F11: asks for the report to be finalised; it waits in the approval queue. */
    public PendingAction requestFinalisation(String caseId) {
        return reports.requestFinalisation(caseId);
    }

    /** F11: writes a final report in each format into dir and returns the files. */
    public List<Path> exportReport(String caseId, java.util.Set<ExportFormat> formats, Path dir) {
        return reports.export(caseId, formats, dir);
    }

    /** F09: the money-flow graph around an account; caseId (may be null) marks what its investigation examined. */
    public MoneyFlowGraph buildNetwork(String accountId, int hops, DateRange range, String caseId) {
        return network.build(accountId, hops, range, caseId);
    }

    /** One account's transactions in a range, oldest first (F09: clicking an account or an edge). */
    public List<Transaction> transactionsFor(String accountId, DateRange range) {
        return transactions.transactionsFor(accountId, range);
    }

    @Override
    public void close() {
        database.close();
    }
}
