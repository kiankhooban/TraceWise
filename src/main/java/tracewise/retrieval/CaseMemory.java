package tracewise.retrieval;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import tracewise.agent.AgentFinding;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Case;
import tracewise.persistence.CaseRepository;

/**
 * Past cases and how they ended, searchable by similarity. Closed and escalated cases are read from storage
 * on first use; cases decided after that are added through remember(). The embedding model is only loaded
 * once there is a case to index.
 */
public class CaseMemory {

    private final CaseRepository cases;
    private final Supplier<EmbeddingRetriever> newRetriever;
    private boolean loaded;
    private EmbeddingRetriever retriever;

    public CaseMemory(CaseRepository cases, Supplier<EmbeddingRetriever> newRetriever) {
        this.cases = cases;
        this.newRetriever = newRetriever;
    }

    /** Called when a case is closed or escalated. Before the first search it is left to the load from storage. */
    public synchronized void remember(Case decidedCase) {
        if (loaded) {
            add(passage(decidedCase).stream().toList());
        }
    }

    public synchronized List<RetrievedPassage> similar(String description, int maxResults) {
        if (!loaded) {
            loaded = true;
            add(cases.findAll().stream().flatMap(c -> passage(c).stream()).toList());
        }
        return retriever == null ? List.of() : retriever.search(description, maxResults);
    }

    private void add(List<Passage> passages) {
        if (passages.isEmpty()) {
            return;
        }
        if (retriever == null) {
            retriever = newRetriever.get();
        }
        retriever.index(passages);
    }

    /** The decision, the indicators and the narrative of the case's latest finding; empty if it has none. */
    public static Optional<Passage> passage(Case c) {
        AgentFinding f = c.latestFinding();
        if (f == null || (c.status() != CaseStatus.CLOSED && c.status() != CaseStatus.ESCALATED)) {
            return Optional.empty();
        }
        return Optional.of(new Passage(c.id(), c.status() + ". " + description(f)));
    }

    /** What a case's latest finding says about the pattern, used to find similar cases. */
    public static String description(AgentFinding f) {
        return (f.indicators().isEmpty() ? "" : "Indicators: " + String.join("; ", f.indicators()) + ". ")
                + f.narrative();
    }
}
