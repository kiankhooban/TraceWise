package tracewise.monitoring;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** alertsByRule counts the new alerts per rule; failedRules holds "ruleId: reason" for rules that crashed. */
public record MonitoringSummary(int thresholdReports, int newAlerts, Map<String, Integer> alertsByRule,
        List<String> failedRules) {

    public MonitoringSummary {
        alertsByRule = new TreeMap<>(alertsByRule);
        failedRules = List.copyOf(failedRules);
    }

    @Override
    public String toString() {
        if (thresholdReports == 0 && newAlerts == 0) {
            return "No new threshold reports or alerts";
        }
        String alerts = newAlerts == 0 ? "no new alerts" : newAlerts + " new alerts: " + alertsByRule.entrySet()
                .stream().map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(", "));
        return thresholdReports + " threshold reports; " + alerts;
    }
}
