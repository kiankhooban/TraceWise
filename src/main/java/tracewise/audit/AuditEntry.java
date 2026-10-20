package tracewise.audit;

import java.time.Instant;
import tracewise.domain.Actor;

public record AuditEntry(Instant timestamp, Actor actor, String eventType, String target, String details,
        String outcome) {
}
