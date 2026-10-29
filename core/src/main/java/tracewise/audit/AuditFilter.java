package tracewise.audit;

import java.time.Instant;
import tracewise.domain.ActorType;

/** Null fields match everything. from is inclusive, to exclusive; actorType tells a person from the agent. */
public record AuditFilter(String eventType, String target, Instant from, Instant to, ActorType actorType) {

    public static final AuditFilter ALL = new AuditFilter(null, null);

    public AuditFilter(String eventType, String target) {
        this(eventType, target, null, null, null);
    }
}
