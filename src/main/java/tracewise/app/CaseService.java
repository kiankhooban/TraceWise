package tracewise.app;

import java.util.List;
import tracewise.domain.Case;
import tracewise.persistence.CaseRepository;

/** F08: case operations for people. Notes and reopening arrive with the case commands. */
public class CaseService {

    private final CaseRepository cases;

    public CaseService(CaseRepository cases) {
        this.cases = cases;
    }

    /** Every case, by id. */
    public List<Case> listCases() {
        return cases.findAll();
    }

    public Case getCase(String caseId) {
        return cases.findById(caseId).orElseThrow(() -> new IllegalArgumentException("No case " + caseId));
    }
}
