package tracewise.metrics;

/** For one suspicion rule: its alerts, how many carry labels, and how many of those involve labelled laundering. */
public record RulePrecision(String ruleId, int alerts, int labelled, int laundering) {

    public Measure precision() {
        return Measure.ratio(laundering, labelled);
    }
}
