package tracewise.agent;

/** What the analyst is looking at when asking: a case, an account, both or neither (null). */
public record AssistantContext(String caseId, String accountId) {

    public static final AssistantContext NONE = new AssistantContext(null, null);
}
