package tracewise.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tracewise.actions.ActionCommand;
import tracewise.actions.AddToWatchlistCommand;
import tracewise.actions.CloseCaseCommand;
import tracewise.actions.CreateTaskCommand;
import tracewise.actions.EscalateCaseCommand;
import tracewise.actions.LinkAlertsCommand;
import tracewise.actions.PendingAction;
import tracewise.actions.ProposalStatus;
import tracewise.actions.SetPriorityCommand;
import tracewise.domain.Actor;
import tracewise.domain.ActorType;

/** Proposals with their command stored as JSON plus its type, so it can be rebuilt and run after a restart. */
public class SqliteApprovalRepository implements ApprovalRepository {

    private static final ObjectMapper JSON = ExactJson.mapper();
    private static final Map<String, Class<? extends ActionCommand>> COMMANDS = Stream.of(CloseCaseCommand.class,
            EscalateCaseCommand.class, SetPriorityCommand.class, AddToWatchlistCommand.class, CreateTaskCommand.class,
            LinkAlertsCommand.class).collect(Collectors.toMap(Class::getSimpleName, Function.identity()));
    private static final String SELECT = "SELECT id, command_type, command, justification, proposed_by, proposed_by_type, "
            + "proposed_at, status, reviewer, reviewer_type, decision_reason, decided_at FROM approvals ";

    private final Database db;

    public SqliteApprovalRepository(Database db) {
        this.db = db;
    }

    @Override
    public void save(PendingAction p) {
        String type = p.command().getClass().getSimpleName();
        if (!COMMANDS.containsKey(type)) {
            throw new IllegalArgumentException("Cannot store a proposal of type " + type);
        }
        Jdbc.update(db, "INSERT INTO approvals (id, command_type, command, justification, proposed_by, proposed_by_type, "
                + "proposed_at, status, reviewer, reviewer_type, decision_reason, decided_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET status = excluded.status, "
                + "reviewer = excluded.reviewer, reviewer_type = excluded.reviewer_type, "
                + "decision_reason = excluded.decision_reason, decided_at = excluded.decided_at",
                p.id(), type, write(p.command()), p.justification(), p.proposedBy().name(), p.proposedBy().type().name(),
                p.proposedAt().toEpochMilli(), p.status().name(), p.reviewer() == null ? null : p.reviewer().name(),
                p.reviewer() == null ? null : p.reviewer().type().name(), p.decisionReason(),
                p.decidedAt() == null ? null : p.decidedAt().toEpochMilli());
    }

    @Override
    public Optional<PendingAction> findById(String id) {
        return Jdbc.query(db, SELECT + "WHERE id = ?", SqliteApprovalRepository::map, id).stream().findFirst();
    }

    @Override
    public List<PendingAction> findPending() {
        return Jdbc.query(db, SELECT + "WHERE status = ? ORDER BY id", SqliteApprovalRepository::map,
                ProposalStatus.PENDING.name());
    }

    @Override
    public String nextId() {
        return String.format("APR-%05d", Jdbc.query(db, "SELECT COUNT(*) FROM approvals", rs -> rs.getLong(1)).get(0) + 1);
    }

    private static String write(ActionCommand command) {
        try {
            return JSON.writeValueAsString(command);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static PendingAction map(ResultSet rs) throws SQLException {
        ActionCommand command;
        try {
            command = JSON.readValue(rs.getString(3), COMMANDS.get(rs.getString(2)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("stored proposal " + rs.getString(1) + " is not readable", e);
        }
        String reviewer = rs.getString(9);
        long decided = rs.getLong(12);
        return new PendingAction(rs.getString(1), command, rs.getString(4),
                new Actor(rs.getString(5), ActorType.valueOf(rs.getString(6))), Instant.ofEpochMilli(rs.getLong(7)),
                ProposalStatus.valueOf(rs.getString(8)),
                reviewer == null ? null : new Actor(reviewer, ActorType.valueOf(rs.getString(10))), rs.getString(11),
                rs.wasNull() ? null : Instant.ofEpochMilli(decided));
    }
}
