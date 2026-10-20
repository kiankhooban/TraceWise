package tracewise.audit;

import java.util.List;

/** Append-only record of every import, run, agent step, action and decision. */
public class AuditLog {

    private final AuditRepository repository;

    public AuditLog(AuditRepository repository) {
        this.repository = repository;
    }

    public void append(AuditEntry entry) {
        repository.append(entry);
    }

    public List<AuditEntry> query(AuditFilter filter) {
        return repository.find(filter);
    }
}
