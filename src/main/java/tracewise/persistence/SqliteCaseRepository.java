package tracewise.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import tracewise.agent.AgentFinding;
import tracewise.agent.AgentOutcome;
import tracewise.agent.AgentTrace;
import tracewise.agent.Claim;
import tracewise.agent.TotalClaim;
import tracewise.agent.TraceStep;
import tracewise.agent.Verdict;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Case;
import tracewise.domain.FollowUpTask;
import tracewise.llm.ModelConfig;
import tracewise.llm.TokenUsage;
import tracewise.verification.VerificationReport;

/** Cases with their linked alerts; each finding is stored as one JSON document with its trace. */
public class SqliteCaseRepository implements CaseRepository {

    private static final ObjectMapper JSON = ExactJson.mapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Database db;

    public SqliteCaseRepository(Database db) {
        this.db = db;
    }

    @Override
    public void save(Case c) {
        db.inTransaction(() -> {
            Jdbc.update(db, "INSERT INTO cases (id, opened_at, status) VALUES (?, ?, ?) "
                    + "ON CONFLICT (id) DO UPDATE SET status = excluded.status", c.id(), c.openedAt().toEpochMilli(),
                    c.status().name());
            for (String alertId : c.alertIds()) {
                Jdbc.update(db, "INSERT OR IGNORE INTO case_alerts (case_id, alert_id) VALUES (?, ?)", c.id(), alertId);
            }
            Jdbc.update(db, "DELETE FROM case_tasks WHERE case_id = ?", c.id());
            List<FollowUpTask> tasks = c.tasks();
            for (int i = 0; i < tasks.size(); i++) {
                Jdbc.update(db, "INSERT INTO case_tasks (case_id, position, description, done) VALUES (?, ?, ?, ?)",
                        c.id(), i, tasks.get(i).description(), tasks.get(i).done() ? 1 : 0);
            }
            int stored = Jdbc.query(db, "SELECT COUNT(*) FROM findings WHERE case_id = ?", rs -> rs.getInt(1), c.id()).get(0);
            List<AgentFinding> findings = c.findings();
            for (AgentFinding f : findings.subList(stored, findings.size())) {
                Jdbc.update(db, "INSERT INTO findings (case_id, body) VALUES (?, ?)", c.id(), write(f));
            }
        });
    }

    @Override
    public Optional<Case> findById(String id) {
        return Jdbc.query(db, "SELECT id, opened_at, status FROM cases WHERE id = ?", rs -> new Case(rs.getString(1),
                Instant.ofEpochMilli(rs.getLong(2)),
                Jdbc.query(db, "SELECT alert_id FROM case_alerts WHERE case_id = ? ORDER BY alert_id", r -> r.getString(1), id),
                CaseStatus.valueOf(rs.getString(3)),
                Jdbc.query(db, "SELECT body FROM findings WHERE case_id = ? ORDER BY seq", r -> read(r.getString(1)), id),
                Jdbc.query(db, "SELECT description, done FROM case_tasks WHERE case_id = ? ORDER BY position",
                        r -> new FollowUpTask(r.getString(1), r.getInt(2) == 1), id)),
                id).stream().findFirst();
    }

    @Override
    public Optional<Case> findByAlert(String alertId) {
        return Jdbc.query(db, "SELECT case_id FROM case_alerts WHERE alert_id = ?", rs -> rs.getString(1), alertId)
                .stream().findFirst().flatMap(this::findById);
    }

    /** shortcut: one query per case; fine for hundreds of cases, batch it if the case list grows large. */
    @Override
    public List<Case> findAll() {
        return Jdbc.query(db, "SELECT id FROM cases ORDER BY id", rs -> rs.getString(1))
                .stream().map(id -> findById(id).orElseThrow()).toList();
    }

    private static String write(AgentFinding f) {
        AgentTrace t = f.trace();
        var trace = new LinkedHashMap<String, Object>();
        trace.put("subject", t.subject());
        trace.put("pricing", t.pricing());
        trace.put("cad_per_usd", t.cadPerUsd());
        trace.put("usage", t.usage());
        trace.put("outcome", t.outcome());
        trace.put("steps", t.steps());
        var body = new LinkedHashMap<String, Object>();
        body.put("verdict", f.verdict());
        body.put("confidence", f.confidence());
        body.put("indicators", f.indicators());
        body.put("claims", f.claims());
        body.put("totals", f.totals());
        body.put("narrative", f.narrative());
        body.put("incomplete", f.incomplete());
        body.put("verification", f.verification());
        body.put("trace", trace);
        try {
            return JSON.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static AgentFinding read(String json) {
        try {
            JsonNode b = JSON.readTree(json);
            JsonNode t = b.get("trace");
            var trace = new AgentTrace(t.get("subject").asText(), JSON.treeToValue(t.get("pricing"), ModelConfig.class),
                    t.get("cad_per_usd").decimalValue());
            for (TraceStep step : JSON.treeToValue(t.get("steps"), TraceStep[].class)) {
                trace.record(step);
            }
            trace.addUsage(JSON.treeToValue(t.get("usage"), TokenUsage.class));
            if (!t.get("outcome").isNull()) {
                trace.finish(JSON.treeToValue(t.get("outcome"), AgentOutcome.class));
            }
            var finding = new AgentFinding(Verdict.valueOf(b.get("verdict").asText()), b.get("confidence").asDouble(),
                    List.of(JSON.treeToValue(b.get("indicators"), String[].class)),
                    List.of(JSON.treeToValue(b.get("claims"), Claim[].class)),
                    List.of(JSON.treeToValue(b.get("totals"), TotalClaim[].class)),
                    b.get("narrative").asText(), b.get("incomplete").asBoolean(), trace);
            if (!b.get("verification").isNull()) {
                finding.attachVerification(JSON.treeToValue(b.get("verification"), VerificationReport.class));
            }
            return finding;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("stored finding is not readable", e);
        }
    }
}
