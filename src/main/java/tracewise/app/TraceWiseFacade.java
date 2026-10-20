package tracewise.app;

import java.nio.file.Path;
import java.util.List;
import tracewise.actions.ApprovalQueue;
import tracewise.actions.PendingAction;
import tracewise.agent.AgentEventListener;
import tracewise.agent.InvestigationResult;
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

/** Facade: the single entry point the GUI and the CLI use; one method per user operation. */
public class TraceWiseFacade implements AutoCloseable {

    private final Database database;
    private final ImportService importService;
    private final MonitoringService monitoringService;
    private final InvestigationService investigationService;
    private final ApprovalQueue approvals;
    private final CaseService caseService;
    private final Actor user;

    TraceWiseFacade(Database database, ImportService importService, MonitoringService monitoringService,
            InvestigationService investigationService, ApprovalQueue approvals, CaseService caseService, Actor user) {
        this.database = database;
        this.importService = importService;
        this.monitoringService = monitoringService;
        this.investigationService = investigationService;
        this.approvals = approvals;
        this.caseService = caseService;
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

    public Case getCase(String caseId) {
        return caseService.getCase(caseId);
    }

    @Override
    public void close() {
        database.close();
    }
}
