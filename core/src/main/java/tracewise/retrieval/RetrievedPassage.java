package tracewise.retrieval;

/** A search result: the passage and its similarity to the query (cosine, rounded to 3 decimals). */
public record RetrievedPassage(String text, String source, double score) {
}
