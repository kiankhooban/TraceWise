package tracewise.actions;

/** What happened to a submitted action. pendingId is set only when it was PROPOSED. */
public record DispatchResult(Status status, String message, String pendingId) {

    public enum Status {
        EXECUTED, PROPOSED, REJECTED
    }
}
