package tracewise.actions;

import java.util.Optional;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Case;
import tracewise.reporting.ReportStatus;
import tracewise.reporting.StrReport;

/**
 * F11: saves a report draft with its case. The agent may do this alone (a draft changes nothing outside the case);
 * a final report and a closed case take no drafts.
 */
public record SaveReportDraftCommand(StrReport report) implements ActionCommand {

    @Override
    public void validate(CommandContext ctx) {
        Case c = ctx.caseFor(report.caseId());
        if (c.status() == CaseStatus.CLOSED) {
            throw new IllegalStateException("Case " + c.id() + " is CLOSED: reopen it before drafting a report");
        }
        if (report.status() != ReportStatus.DRAFT
                || c.report().filter(r -> r.status() == ReportStatus.FINAL).isPresent()) {
            throw new IllegalStateException("The report for case " + c.id() + " is final and locked");
        }
    }

    @Override
    public void execute(CommandContext ctx) {
        Case c = ctx.caseFor(report.caseId());
        c.saveReport(report);
        ctx.cases().save(c);
    }

    @Override
    public String describe() {
        return "Save the report draft for case " + report.caseId();
    }

    @Override
    public Optional<String> targetCaseId() {
        return Optional.of(report.caseId());
    }

    @Override
    public String target() {
        return report.caseId();
    }
}
