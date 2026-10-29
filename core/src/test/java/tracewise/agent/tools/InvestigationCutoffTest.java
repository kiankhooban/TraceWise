package tracewise.agent.tools;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Found in review 11: the cutoff was worked out again on every ledger call (a case load and a lookup per flagged
 * transaction each time), and from the case's opening alert rather than the alert under investigation.
 */
class InvestigationCutoffTest {

    final List<String> lookups = new ArrayList<>();
    final ActionScope scope = new ActionScope(null);
    final InvestigationCutoff cutoff = new InvestigationCutoff(scope, alertId -> {
        lookups.add(alertId);
        return Optional.of(Instant.parse(alertId.equals("ALR-00001") ? "2026-09-05T00:00:00Z" : "2026-10-05T00:00:00Z"));
    });

    @Test
    void itIsTheInvestigatedAlertsCutoffWorkedOutOnce() {
        scope.begin("CAS-00001", "ALR-00002");

        assertEquals(Optional.of(Instant.parse("2026-10-05T00:00:00Z")), cutoff.get());
        cutoff.get();
        assertEquals(List.of("ALR-00002"), lookups);
    }

    @Test
    void aNewInvestigationGetsItsOwnCutoff() {
        scope.begin("CAS-00001", "ALR-00002");
        cutoff.get();
        scope.end();
        scope.begin("CAS-00001", "ALR-00001");

        assertEquals(Optional.of(Instant.parse("2026-09-05T00:00:00Z")), cutoff.get());
    }

    @Test
    void withoutAnInvestigationThereIsNoCutoff() {
        assertTrue(cutoff.get().isEmpty());
        assertTrue(lookups.isEmpty());
    }
}
