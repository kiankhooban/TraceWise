package tracewise.agent;

public enum Verdict {

    ESCALATE, DISMISS, INCONCLUSIVE;

    /** Only an ESCALATE finding supports a proposal to escalate the case. */
    public boolean supportsEscalation() {
        return this == ESCALATE;
    }

    /** Only a DISMISS finding supports a proposal to close the case. */
    public boolean supportsClose() {
        return this == DISMISS;
    }
}
