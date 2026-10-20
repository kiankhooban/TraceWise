package tracewise.ui.cli;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import tracewise.monitoring.DetectionRule;
import tracewise.monitoring.RuleConfig;

@Command(name = "rules", description = "Monitoring rule configuration.",
        subcommands = {RulesCli.ListRules.class, RulesCli.SetRule.class})
public class RulesCli {

    @Command(name = "list", description = "Show each rule, whether it is enabled, and its parameters.")
    static class ListRules extends CliCommandBase {

        @Override
        protected Object execute() {
            var view = new java.util.ArrayList<Map<String, Object>>();
            for (DetectionRule r : facade().listRules()) {
                var row = new LinkedHashMap<String, Object>();
                row.put("id", r.id());
                row.put("kind", r.kind());
                row.put("enabled", r.config().enabled());
                row.put("parameters", new TreeMap<>(r.config().parameters()));
                view.add(row);
            }
            return view;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void print(Object result) {
            if (json) {
                super.print(result);
                return;
            }
            for (Map<String, Object> r : (List<Map<String, Object>>) result) {
                String params = ((Map<String, BigDecimal>) r.get("parameters")).entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue().toPlainString()).collect(Collectors.joining(", "));
                out().printf("%-3s %-9s %-10s %s%n", r.get("id"), (boolean) r.get("enabled") ? "enabled" : "disabled",
                        r.get("kind"), params);
            }
        }
    }

    @Command(name = "set", description = "Change a rule (Compliance Supervisor only).")
    static class SetRule extends CliCommandBase {

        @Parameters(paramLabel = "RULE", description = "Rule id, for example R3.")
        String ruleId;

        @Option(names = "--set", paramLabel = "NAME=VALUE", description = "Parameter to change; repeatable.")
        Map<String, BigDecimal> changes = new HashMap<>();

        @ArgGroup
        Toggle toggle;

        static class Toggle {
            @Option(names = "--enable", required = true)
            boolean enable;

            @Option(names = "--disable", required = true)
            boolean disable;
        }

        @Override
        protected Object execute() {
            DetectionRule rule = facade().listRules().stream().filter(r -> r.id().equals(ruleId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("no rule with id " + ruleId));
            var parameters = new HashMap<>(rule.config().parameters());
            parameters.putAll(changes);
            boolean enabled = toggle == null ? rule.config().enabled() : toggle.enable;
            facade().updateRule(ruleId, new RuleConfig(enabled, parameters));
            return ruleId + " updated";
        }
    }
}
