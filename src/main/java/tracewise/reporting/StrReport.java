package tracewise.reporting;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import tracewise.agent.AgentFinding;
import tracewise.domain.Transaction;
import tracewise.verification.ClaimCheck;

/**
 * F11: a draft suspicious transaction report, structured like FINTRAC's STR: the subject account, the
 * transactions, the grounds for suspicion and the actions taken. The structured fields come from the case;
 * the analyst edits the two text fields. Once locked (after an approved finalisation) it cannot change.
 */
public class StrReport {

    private final String caseId;
    private final String subjectAccountId;
    private final List<Transaction> transactions;
    private final String groundsForSuspicion;
    private final String actionsTaken;
    private ReportStatus status;
    private Instant finalisedAt;

    public StrReport(String caseId, String subjectAccountId, List<Transaction> transactions, String groundsForSuspicion,
            String actionsTaken, ReportStatus status, Instant finalisedAt) {
        this.caseId = caseId;
        this.subjectAccountId = subjectAccountId;
        this.transactions = List.copyOf(transactions);
        this.groundsForSuspicion = groundsForSuspicion;
        this.actionsTaken = actionsTaken;
        this.status = status;
        this.finalisedAt = finalisedAt;
    }

    public static StrReport draft(String caseId, String subjectAccountId, List<Transaction> transactions,
            String groundsForSuspicion, String actionsTaken) {
        return new StrReport(caseId, subjectAccountId, transactions, groundsForSuspicion, actionsTaken, ReportStatus.DRAFT,
                null);
    }

    /**
     * Why this report cannot be finalised yet, empty when it can: a required field is empty, the case has no
     * verified finding, or the text still mentions a claim the verifier rejected (F05).
     */
    public List<String> finalisationProblems(AgentFinding finding) {
        var problems = new ArrayList<String>();
        if (blank(subjectAccountId)) {
            problems.add("Missing: subject account");
        }
        if (transactions.isEmpty()) {
            problems.add("Missing: transactions");
        }
        if (blank(groundsForSuspicion)) {
            problems.add("Missing: grounds for suspicion");
        }
        if (blank(actionsTaken)) {
            problems.add("Missing: actions taken");
        }
        if (finding == null || finding.verification() == null) {
            problems.add("The case has no verified finding");
            return problems;
        }
        Map<String, ClaimCheck> rejected = finding.verification().rejected().stream()
                .collect(Collectors.toMap(ClaimCheck::claim, Function.identity(), (a, b) -> a));
        String text = groundsForSuspicion + "\n" + actionsTaken;
        var mentioned = new ArrayList<ClaimCheck>();
        finding.claims().stream().filter(c -> rejected.containsKey(c.describe()))
                .filter(c -> mentions(text, c.transactionId())).forEach(c -> mentioned.add(rejected.get(c.describe())));
        finding.totals().stream().filter(t -> rejected.containsKey(t.describe()))
                .filter(t -> mentions(text, t.amountCad().toPlainString()) || mentions(text, grouped(t.amountCad())))
                .forEach(t -> mentioned.add(rejected.get(t.describe())));
        finding.indicators().stream().filter(i -> rejected.containsKey("Indicator: " + i)).filter(i -> mentions(text, i))
                .forEach(i -> mentioned.add(rejected.get("Indicator: " + i)));
        mentioned.forEach(c -> problems.add("Remove the rejected claim from the text: " + c.claim() + " (" + c.reason() + ")"));
        return problems;
    }

    /** A new draft with the analyst's text; the structured fields stay as drafted. */
    public StrReport withText(String groundsForSuspicion, String actionsTaken) {
        requireDraft();
        return draft(caseId, subjectAccountId, transactions, groundsForSuspicion, actionsTaken);
    }

    /** Called when the finalisation is approved. */
    public void lock(Instant at) {
        requireDraft();
        status = ReportStatus.FINAL;
        finalisedAt = at;
    }

    private void requireDraft() {
        if (status == ReportStatus.FINAL) {
            throw new IllegalStateException("The report for case " + caseId + " is final and locked");
        }
    }

    /** The marker as a whole word, so TX-9 is not found inside TX-90. */
    private static boolean mentions(String text, String marker) {
        return Pattern.compile("(?<!\\w)" + Pattern.quote(marker) + "(?!\\w)", Pattern.CASE_INSENSITIVE).matcher(text)
                .find();
    }

    private static String grouped(BigDecimal amount) {
        return String.format(Locale.ROOT, "%,." + Math.max(amount.scale(), 0) + "f", amount);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    public String caseId() { return caseId; }
    public String subjectAccountId() { return subjectAccountId; }
    public List<Transaction> transactions() { return transactions; }
    public String groundsForSuspicion() { return groundsForSuspicion; }
    public String actionsTaken() { return actionsTaken; }
    public ReportStatus status() { return status; }
    public Instant finalisedAt() { return finalisedAt; }
}
