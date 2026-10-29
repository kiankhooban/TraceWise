package tracewise.persistence;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import tracewise.monitoring.RuleConfig;

/** Parameters are stored as "name=value;name=value". */
public class SqliteRuleConfigRepository implements RuleConfigRepository {

    private final Database db;

    public SqliteRuleConfigRepository(Database db) {
        this.db = db;
    }

    @Override
    public void save(String ruleId, RuleConfig config) {
        String parameters = new TreeMap<>(config.parameters()).entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue().toPlainString()).collect(Collectors.joining(";"));
        Jdbc.update(db, "INSERT OR REPLACE INTO rule_configs (rule_id, enabled, parameters) VALUES (?, ?, ?)",
                ruleId, config.enabled() ? 1 : 0, parameters);
    }

    @Override
    public Optional<RuleConfig> load(String ruleId) {
        return Jdbc.query(db, "SELECT enabled, parameters FROM rule_configs WHERE rule_id = ?",
                rs -> new RuleConfig(rs.getInt(1) == 1, Arrays.stream(rs.getString(2).split(";"))
                        .map(p -> p.split("=")).collect(Collectors.toMap(p -> p[0], p -> new BigDecimal(p[1])))),
                ruleId).stream().findFirst();
    }
}
