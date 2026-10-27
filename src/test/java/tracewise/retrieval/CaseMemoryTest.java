package tracewise.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tracewise.agent.AgentFinding;
import tracewise.agent.Verdict;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Case;
import tracewise.persistence.CaseRepository;

class CaseMemoryTest {

    private final List<Case> stored = new ArrayList<>();
    private final WordCountEmbeddingModel model = new WordCountEmbeddingModel();
    private final CaseMemory memory = new CaseMemory(new ListCases(), () -> new EmbeddingRetriever(model, 0.5));

    static Case decided(String id, CaseStatus status, String narrative) {
        var finding = new AgentFinding(Verdict.ESCALATE, 0.9, List.of("Structuring"), List.of(), List.of(), narrative,
                false, null);
        return new Case(id, Instant.parse("2026-09-01T00:00:00Z"), List.of("ALR-1"), status, List.of(finding));
    }

    @Test
    void anEmptyMemoryFindsNothingWithoutLoadingTheModel() {
        assertEquals(List.of(), memory.similar("cash deposits under the threshold", 3));
        assertEquals(0, model.calls);
    }

    @Test
    void findsClosedAndEscalatedCasesWithTheirDecisionButNotOpenOnes() {
        stored.add(decided("CAS-1", CaseStatus.ESCALATED, "Cash deposits just under the threshold."));
        stored.add(decided("CAS-2", CaseStatus.UNDER_INVESTIGATION, "Cash deposits under the threshold."));
        stored.add(decided("CAS-3", CaseStatus.CLOSED, "Regular payroll."));

        var results = memory.similar("cash deposit threshold", 5);

        assertEquals(List.of("CAS-1"), results.stream().map(RetrievedPassage::source).toList());
        assertEquals("ESCALATED. Indicators: Structuring. Cash deposits just under the threshold.", results.get(0).text());
        assertEquals("CLOSED. Indicators: Structuring. Regular payroll.", memory.similar("payroll", 5).get(0).text());
    }

    @Test
    void aCaseDecidedAfterTheMemoryLoadedIsRemembered() {
        memory.similar("payroll", 3);

        memory.remember(decided("CAS-3", CaseStatus.CLOSED, "Regular payroll."));

        assertEquals("CAS-3", memory.similar("payroll", 3).get(0).source());
    }

    @Test
    void aCaseRememberedBeforeTheMemoryLoadedIsFoundOnce() {
        var c = decided("CAS-3", CaseStatus.CLOSED, "Regular payroll.");
        stored.add(c);
        memory.remember(c);

        assertEquals(1, memory.similar("payroll", 3).size());
    }

    @Test
    void aCaseWithoutAFindingIsLeftOut() {
        stored.add(new Case("CAS-4", Instant.parse("2026-09-01T00:00:00Z"), List.of("ALR-1"), CaseStatus.CLOSED));

        assertEquals(List.of(), memory.similar("payroll", 3));
    }

    private final class ListCases implements CaseRepository {
        @Override
        public void save(Case c) {
            stored.add(c);
        }

        @Override
        public Optional<Case> findById(String id) {
            return stored.stream().filter(c -> c.id().equals(id)).findFirst();
        }

        @Override
        public Optional<Case> findByAlert(String alertId) {
            return Optional.empty();
        }

        @Override
        public List<Case> findAll() {
            return List.copyOf(stored);
        }
    }
}
