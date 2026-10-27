package tracewise.app;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import tracewise.actions.ActionCommand;
import tracewise.actions.ApprovalQueue;
import tracewise.actions.CommandDispatcher;
import tracewise.actions.DispatchResult;
import tracewise.actions.FinaliseReportCommand;
import tracewise.actions.PendingAction;
import tracewise.actions.ProposalStatus;
import tracewise.actions.SaveReportDraftCommand;
import tracewise.agent.AgentFinding;
import tracewise.agent.AgentTrace;
import tracewise.audit.AuditEntry;
import tracewise.audit.AuditLog;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Account;
import tracewise.domain.Actor;
import tracewise.domain.Case;
import tracewise.domain.FollowUpTask;
import tracewise.domain.Transaction;
import tracewise.llm.LLMClient;
import tracewise.llm.LLMMessage;
import tracewise.llm.LLMRequest;
import tracewise.llm.LLMResponse;
import tracewise.llm.ModelConfig;
import tracewise.persistence.AlertRepository;
import tracewise.persistence.CaseRepository;
import tracewise.persistence.TransactionRepository;
import tracewise.reporting.ExportFormat;
import tracewise.reporting.ReportExporter;
import tracewise.reporting.ReportStatus;
import tracewise.reporting.StrReport;
import tracewise.verification.ClaimCheck;

/**
 * F11: drafts a suspicious transaction report from the case's verified finding, saves the analyst's edits,
 * requests finalisation through the approval queue, and exports final reports. The structured fields and every
 * rule are deterministic; the model writes only the grounds-for-suspicion narrative, from confirmed facts.
 */
public class ReportService {

    static final String SYSTEM_PROMPT = """
            You write the grounds-for-suspicion narrative of a draft suspicious transaction report (STR) for FINTRAC, \
            Canada's financial intelligence unit. An AML analyst reviews and edits it before anything is final.
            Rules:
            - Use only the facts given. Every transaction id, amount and date you mention must appear in the verified \
            transactions, written exactly as given.
            - Never mention anything listed under "Rejected claims": the verifier found it false or unsupported.
            - Explain who, what, when, where, how and why: the subject account, the pattern of transactions, the \
            counterparties, and the FINTRAC indicators that apply.
            - State reasonable grounds to suspect. Do not conclude guilt or name an offence.
            - Plain prose, 150 to 300 words, no headings, no lists, no markdown. Reply with the narrative only.""";

    private final CaseRepository cases;
    private final AlertRepository alerts;
    private final TransactionRepository accounts;
    private final Supplier<LLMClient> llm;
    private final CommandDispatcher dispatcher;
    private final ApprovalQueue approvals;
    private final AuditLog audit;
    private final List<ReportExporter> exporters;
    private final Actor user;
    private final ModelConfig pricing;
    private final BigDecimal cadPerUsd;

    public ReportService(CaseRepository cases, AlertRepository alerts, TransactionRepository accounts,
            Supplier<LLMClient> llm, CommandDispatcher dispatcher, ApprovalQueue approvals, AuditLog audit,
            List<ReportExporter> exporters, Actor user, ModelConfig pricing, BigDecimal cadPerUsd) {
        this.cases = cases;
        this.alerts = alerts;
        this.accounts = accounts;
        this.llm = llm;
        this.dispatcher = dispatcher;
        this.approvals = approvals;
        this.audit = audit;
        this.exporters = List.copyOf(exporters);
        this.user = user;
        this.pricing = pricing;
        this.cadPerUsd = cadPerUsd;
    }

    public Optional<StrReport> report(String caseId) {
        return caseOrThrow(caseId).report();
    }

    /**
     * Drafts the report, replacing an earlier draft. The subject and transactions come from the case and its
     * confirmed claims; the model writes the narrative; the agent saves the draft (autonomous: draft only).
     */
    public StrReport draft(String caseId) {
        Case c = caseOrThrow(caseId);
        // checked before paying for the model call; the save command checks again
        if (c.status() == CaseStatus.CLOSED) {
            throw new IllegalStateException("Case " + caseId + " is CLOSED: reopen it before drafting a report");
        }
        if (c.report().filter(r -> r.status() == ReportStatus.FINAL).isPresent()) {
            throw new IllegalStateException("The report for case " + caseId + " is final and locked");
        }
        AgentFinding finding = c.latestFinding();
        if (finding == null || finding.verification() == null) {
            throw new IllegalStateException("Case " + caseId + " has no verified finding yet: investigate it first");
        }
        String subject = alerts.findById(c.alertIds().get(0)).map(a -> a.accountId()).orElse("");
        List<Transaction> reported = confirmedTransactions(finding);
        var trace = new AgentTrace("report " + caseId, pricing, cadPerUsd);
        String narrative = narrative(c, subject, reported, finding, trace);
        var report = StrReport.draft(caseId, subject, reported, narrative, actionsTaken(c, subject, finding));
        submit(new SaveReportDraftCommand(report), Actor.AGENT, "drafted by " + pricing.modelName() + " for "
                + user.name() + " from the verified finding (" + trace.usage().inputTokens() + " input and "
                + trace.usage().outputTokens() + " output tokens, CAD " + trace.costCad().toPlainString() + ")");
        return report;
    }

    /** The analyst's edits to the two text fields. */
    public StrReport save(String caseId, String groundsForSuspicion, String actionsTaken) {
        StrReport edited = draftOrThrow(caseId).withText(groundsForSuspicion == null ? "" : groundsForSuspicion,
                actionsTaken == null ? "" : actionsTaken);
        submit(new SaveReportDraftCommand(edited), user, "edited by the analyst");
        return edited;
    }

    /** Refused while a field is empty, a rejected claim remains, or the case is not escalated; else it waits. */
    public PendingAction requestFinalisation(String caseId) {
        StrReport draft = draftOrThrow(caseId);
        approvals.forCase(caseId).stream()
                .filter(p -> p.status() == ProposalStatus.PENDING && p.command() instanceof FinaliseReportCommand)
                .findFirst().ifPresent(p -> {
                    throw new IllegalStateException("The report for case " + caseId + " is already waiting for approval as "
                            + p.id());
                });
        DispatchResult r = submit(FinaliseReportCommand.of(draft), user, "the report is complete; finalisation requested");
        return approvals.find(r.pendingId());
    }

    /** Only a final report is exported. A failed write leaves it final, so the export can be retried. */
    public List<Path> export(String caseId, Set<ExportFormat> formats, Path dir) {
        if (formats.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one format");
        }
        StrReport report = caseOrThrow(caseId).report()
                .orElseThrow(() -> new IllegalStateException("Case " + caseId + " has no report"));
        if (report.status() != ReportStatus.FINAL) {
            throw new IllegalStateException("Only a finalised report can be exported; the report for case " + caseId
                    + " is a draft");
        }
        var files = new ArrayList<Path>();
        try {
            Files.createDirectories(dir);
            for (ReportExporter exporter : exporters) {
                if (formats.contains(exporter.format())) {
                    files.add(exporter.export(report, dir));
                }
            }
        } catch (IOException e) {
            audit.append(new AuditEntry(Instant.now(), user, "REPORT_EXPORTED", caseId, "Export to " + dir + " failed: "
                    + e, "FAILED"));
            throw new IllegalStateException("Could not write the report to " + dir + " (" + e
                    + "). The report stays final: try again.", e);
        }
        audit.append(new AuditEntry(Instant.now(), user, "REPORT_EXPORTED", caseId, "Exported to " + files, "OK"));
        return files;
    }

    /** The transactions behind the confirmed claims and totals, in time order; rejected ones never enter the report. */
    private List<Transaction> confirmedTransactions(AgentFinding finding) {
        Set<String> confirmed = finding.verification().checks().stream().filter(ClaimCheck::confirmed)
                .map(ClaimCheck::claim).collect(Collectors.toSet());
        var ids = new LinkedHashSet<String>();
        finding.claims().stream().filter(c -> confirmed.contains(c.describe())).forEach(c -> ids.add(c.transactionId()));
        finding.totals().stream().filter(t -> confirmed.contains(t.describe())).forEach(t -> ids.addAll(t.transactionIds()));
        return ids.stream().map(accounts::findTransaction).flatMap(Optional::stream)
                .sorted(Comparator.comparing(Transaction::timestamp).thenComparing(Transaction::id)).toList();
    }

    private String narrative(Case c, String subject, List<Transaction> reported, AgentFinding finding, AgentTrace trace) {
        var facts = new StringBuilder("Case: ").append(c.id()).append("\nSubject account: ").append(subject)
                .append("\nAlerts:\n");
        for (String alertId : c.alertIds()) {
            alerts.findById(alertId).ifPresent(a -> facts.append("- ").append(a.id()).append(": rule ").append(a.ruleId())
                    .append(" on account ").append(a.accountId()).append(", total ").append(a.totalCad().toPlainString())
                    .append(" CAD\n"));
        }
        facts.append("Verified transactions (id | time UTC | from | to | amount CAD | channel | counterparty country):\n");
        for (Transaction t : reported) {
            facts.append("- ").append(String.join(" | ", t.id(), t.timestamp().toString(), t.senderAccountId(),
                    t.receiverAccountId(), t.amountCad().toPlainString(), t.channel().name(),
                    t.counterpartyCountry() == null ? "" : t.counterpartyCountry())).append('\n');
        }
        var checks = finding.verification().checks();
        facts.append("Verified totals and FINTRAC indicators:\n");
        checks.stream().filter(ClaimCheck::confirmed).filter(k -> !k.claim().startsWith("Transaction "))
                .forEach(k -> facts.append("- ").append(k.claim()).append('\n'));
        facts.append("Investigation summary (verdict ").append(finding.verdict()).append("):\n")
                .append(finding.narrative()).append('\n');
        var rejected = finding.verification().rejected();
        if (!rejected.isEmpty()) {
            facts.append("Rejected claims (do not use):\n");
            rejected.forEach(k -> facts.append("- ").append(k.claim()).append(" (").append(k.reason()).append(")\n"));
        }
        LLMResponse response;
        try {
            response = llm.get().chat(new LLMRequest(SYSTEM_PROMPT, List.of(new LLMMessage.User(facts.toString())),
                    List.of()));
        } catch (RuntimeException e) {
            throw new IllegalStateException("The narrative could not be drafted: " + e.getMessage(), e);
        }
        if (response.usage() != null) {
            trace.addUsage(response.usage());
        }
        String text = response.text().strip();
        if (text.isEmpty()) {
            throw new IllegalStateException("The model returned no narrative; try again");
        }
        return text;
    }

    /** What the bank did, from the case itself; the analyst can edit it. */
    private String actionsTaken(Case c, String subject, AgentFinding finding) {
        var lines = new ArrayList<String>();
        lines.add("Alerts reviewed: " + String.join(", ", c.alertIds()) + ".");
        lines.add("Investigated with TraceWise; the finding recommends " + finding.verdict() + " (confidence "
                + String.format(java.util.Locale.ROOT, "%.2f", finding.confidence()) + "), "
                + finding.verification().summary() + ".");
        if (accounts.findAccount(subject).filter(Account::watchlisted).isPresent()) {
            lines.add("Account " + subject + " added to the watchlist for continued monitoring.");
        }
        c.tasks().stream().map(FollowUpTask::description).forEach(t -> lines.add("Follow-up task: " + t));
        lines.add(c.status() == CaseStatus.ESCALATED ? "Case escalated for reporting after approval."
                : "Case status: " + c.status() + ".");
        return String.join("\n", lines);
    }

    private DispatchResult submit(ActionCommand command, Actor actor, String justification) {
        DispatchResult r = dispatcher.submit(command, actor, justification);
        if (r.status() == DispatchResult.Status.REJECTED) {
            throw new IllegalStateException(r.message());
        }
        return r;
    }

    private StrReport draftOrThrow(String caseId) {
        return caseOrThrow(caseId).report()
                .orElseThrow(() -> new IllegalStateException("Case " + caseId + " has no report draft: draft it first"));
    }

    private Case caseOrThrow(String caseId) {
        return cases.findById(caseId).orElseThrow(() -> new IllegalArgumentException("No case " + caseId));
    }
}
