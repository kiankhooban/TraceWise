package tracewise.audit;

import java.util.List;

public interface AuditRepository {

    void append(AuditEntry entry);

    List<AuditEntry> find(AuditFilter filter);
}
