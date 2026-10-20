package tracewise.audit;

/** Null fields match everything. */
public record AuditFilter(String eventType, String target) {
    public static final AuditFilter ALL = new AuditFilter(null, null);
}
