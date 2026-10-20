package tracewise.domain;

/** Who performed an action: a person, the agent, or TraceWise itself. */
public record Actor(String name, ActorType type) {
    public static final Actor SYSTEM = new Actor("TraceWise", ActorType.SYSTEM);
    public static final Actor AGENT = new Actor("TraceWise agent", ActorType.AGENT);

    /** Analysts and supervisors; only they may decide what the agent proposes. */
    public boolean isHuman() {
        return type == ActorType.ANALYST || type == ActorType.SUPERVISOR;
    }
}
