package tracewise.domain;

/** Who performed an action: a person, the agent, or TraceWise itself. */
public record Actor(String name, ActorType type) {
    public static final Actor SYSTEM = new Actor("TraceWise", ActorType.SYSTEM);
}
