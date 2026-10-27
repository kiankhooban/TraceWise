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
    private final Retriever knowledge;
    private final CaseMemory memory;
    private final Actor user;

    TraceWiseFacade(Database database, ImportService importService, MonitoringService monitoringService,
            InvestigationService investigationService, TriageService triageService, ApprovalQueue approvals,
            CaseService caseService, AssistantService assistant, Retriever knowledge, CaseMemory memory, Actor user) {
        this.database = database;
        this.importService = importService;
        this.monitoringService = monitoringService;
        this.investigationService = investigationService;
        this.triageService = triageService;
        this.approvals = approvals;
        this.caseService = caseService;
        this.assistant = assistant;
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

    @Override
    public void close() {
        database.close();
    }
}
