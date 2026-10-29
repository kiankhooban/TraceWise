package tracewise.cases.state;

/** Final: an escalated case has gone to the reporting officer and allows no further transitions. */
public class EscalatedState extends CaseState {

    @Override
    public CaseStatus status() {
        return CaseStatus.ESCALATED;
    }
}
