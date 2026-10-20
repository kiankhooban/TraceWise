package tracewise.agent;

/** How an agent run ended. INCOMPLETE: a turn or cost limit forced an early best answer. */
public enum OutcomeStatus {
    COMPLETED, INCOMPLETE, FAILED, CANCELLED
}
