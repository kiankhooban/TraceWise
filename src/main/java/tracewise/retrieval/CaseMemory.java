package tracewise.retrieval;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import tracewise.agent.AgentFinding;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Case;
import tracewise.persistence.CaseRepository;

/**
 * Past cases and how they ended, searchable by similarity. The closed and escalated cases are read from
 * storage on the first search and again after a decision, so only committed decisions are remembered. The
 * embedding model is only loaded once there is a case to index.
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

    /**
     * Called when a case is closed or escalated, inside that decision's transaction. It embeds nothing, so it
     * cannot fail the decision: the next search rebuilds from storage, which leaves out a rolled-back decision.
     */
    public synchronized void remember(Case decidedCase) {
        if (passage(decidedCase).isPresent()) {
            loaded = false;
        }
    }

    public synchronized List<RetrievedPassage> similar(String description, int maxResults) {
        if (!loaded) {
            retriever = null;
            add(cases.findAll().stream().flatMap(c -> passage(c).stream()).toList());
            loaded = true; // only after success: a failed load is tried again on the next search
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

    /** What a case's latest finding says about the pattern: its decision is added by passage(). */
    public static String description(AgentFinding f) {
        return f.indicators().isEmpty() ? f.narrative() : pattern(f) + " " + f.narrative();
    }

    /**
     * The FINTRAC indicators of a finding, or its narrative if it has none: the query for cases similar to it.
     * Narratives share one style and are full of figures, so two of them score about 0.85 whatever the pattern.
     */
    public static String pattern(AgentFinding f) {
        if (f.indicators().isEmpty()) {
            return f.narrative();
        }
        String list = String.join("; ", f.indicators());
        return "Indicators: " + list + (list.endsWith(".") ? "" : ".");
    }
}
