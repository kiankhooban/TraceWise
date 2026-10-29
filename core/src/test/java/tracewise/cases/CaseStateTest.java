package tracewise.cases;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tracewise.cases.state.CaseStatus;
import tracewise.cases.state.IllegalTransitionException;
import tracewise.domain.Case;

class CaseStateTest {

    static Case newCase() {
        return new Case("CAS-00001", Instant.parse("2026-10-20T12:00:00Z"), List.of("ALR-00001"));
    }

    @Test
    void followsTheLifecycleToEscalation() {
        var c = newCase();
        assertEquals(CaseStatus.OPEN, c.status());
        c.startInvestigation();
        c.startInvestigation(); // a re-run is allowed
        assertEquals(CaseStatus.UNDER_INVESTIGATION, c.status());
        c.submitForApproval();
        assertEquals(CaseStatus.PENDING_APPROVAL, c.status());
        c.returnToInvestigation();
        c.submitForApproval();
        c.escalate();
        assertEquals(CaseStatus.ESCALATED, c.status());
    }

    @Test
    void escalatedIsFinal() {
        var c = newCase();
        c.startInvestigation();
        c.submitForApproval();
        c.escalate();
        var e = assertThrows(IllegalTransitionException.class, c::close);
        assertTrue(e.getMessage().contains("ESCALATED"), e.getMessage());
    }

    @Test
    void invalidTransitionNamesTheCurrentState() {
        var c = newCase();
        var e = assertThrows(IllegalTransitionException.class, c::escalate);
        assertEquals("Cannot escalate a case that is OPEN", e.getMessage());
        assertEquals(CaseStatus.OPEN, c.status());
    }

    @Test
    void closedCaseReopensOnlyWithAReason() {
        var c = newCase();
        c.close();
        assertThrows(IllegalArgumentException.class, () -> c.reopen("  "));
        assertEquals(CaseStatus.CLOSED, c.status());
        c.reopen("New transactions arrived");
        assertEquals(CaseStatus.OPEN, c.status());
        assertThrows(IllegalTransitionException.class, () -> c.reopen("again"));
    }

    @Test
    void aStoredCaseResumesInItsSavedState() {
        var c = new Case("CAS-00002", Instant.EPOCH, List.of("ALR-00002"), CaseStatus.PENDING_APPROVAL);
        assertThrows(IllegalTransitionException.class, c::startInvestigation);
        c.close();
        assertEquals(CaseStatus.CLOSED, c.status());
    }
}
