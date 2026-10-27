package tracewise.retrieval;

import java.util.List;

/**
 * Strategy: how passages are found for a query. The agent's tools and the analyst's search depend only on
 * this, so keyword or hybrid search can replace embeddings without changing them.
 */
@FunctionalInterface
public interface Retriever {

    /** The best matches, best first, at most maxResults; empty when nothing is relevant enough. */
    List<RetrievedPassage> search(String query, int maxResults);
}
