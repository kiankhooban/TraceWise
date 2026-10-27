package tracewise.actions;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Case;
import tracewise.reporting.ReportStatus;
import tracewise.reporting.StrReport;

/**
 * F11: locks an escalated case's report. Always waits for approval, and carries the text it was requested
 * for, so a draft edited after the request expires the proposal instead of being finalised unseen.
 */
public record FinaliseReportCommand(String caseId, String groundsForSuspicion, String actionsTaken)
        implements ActionCommand {

    public static FinaliseReportCommand of(StrReport draft) {
        return new FinaliseReportCommand(draft.caseId(), draft.groundsForSuspicion(), draft.actionsTaken());
    }

    @Override
    public void validate(CommandContext ctx) {
        report(ctx.caseFor(caseId));
    }

    @Override
    public void execute(CommandContext ctx) {
        Case c = ctx.caseFor(caseId);
        report(c).lock(Instant.now());
        ctx.cases().save(c);
    }

    private StrReport report(Case c) {
        if (c.status() != CaseStatus.ESCALATED) {
            throw new IllegalStateException("Case " + caseId + " is " + c.status()
                    + ": a report can be finalised only after the case's escalation is approved");
        }
        StrReport r = c.report().orElseThrow(() -> new IllegalStateException("Case " + caseId + " has no report draft"));
        if (r.status() == ReportStatus.FINAL) {
            throw new IllegalStateException("The report for case " + caseId + " is already final");
        }
        if (!r.groundsForSuspicion().equals(groundsForSuspicion) || !r.actionsTaken().equals(actionsTaken)) {
            throw new IllegalStateException("The report draft for case " + caseId
                    + " changed after finalisation was requested: request it again");
        }
        List<String> problems = r.finalisationProblems(c.latestFinding());
        if (!problems.isEmpty()) {
            throw new IllegalStateException("The report cannot be finalised: " + String.join("; ", problems));
        }
        return r;
    }

    @Override
    public String describe() {
        return "Finalise the suspicious transaction report for case " + caseId;
    }

    @Override
    public Optional<String> targetCaseId() {
        return Optional.of(caseId);
    }

    @Override
    public String target() {
        return caseId;
    }
}
