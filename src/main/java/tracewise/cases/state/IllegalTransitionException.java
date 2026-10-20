package tracewise.cases.state;

/** A case operation its current state does not allow; the message names that state. */
public class IllegalTransitionException extends RuntimeException {

    public IllegalTransitionException(String action, CaseStatus current) {
        super("Cannot " + action + " a case that is " + current);
    }
}
