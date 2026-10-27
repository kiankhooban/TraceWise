package tracewise.retrieval;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/** Concrete strategy: semantic search with a local embedding model (bge-small-en-v1.5 in the application). */
public class EmbeddingRetriever implements Retriever {

    /**
     * Measured on the FINTRAC corpus with bge-small-en-v1.5: pattern descriptions score 0.75 to 0.94 against
     * their indicator, unrelated text at most about 0.66.
     */
    public static final double MIN_SCORE = 0.70;

    private final EmbeddingModel model;
    private final double minScore;
    private final List<Passage> passages = new ArrayList<>();
    private final List<Embedding> embeddings = new ArrayList<>();

    public EmbeddingRetriever(EmbeddingModel model, double minScore) {
        this.model = model;
        this.minScore = minScore;
    }

    public synchronized void index(List<Passage> batch) {
        if (batch.isEmpty()) {
            return;
        }
        embeddings.addAll(model.embedAll(batch.stream().map(p -> TextSegment.from(p.text())).toList()).content());
        passages.addAll(batch);
    }

    @Override
    public synchronized List<RetrievedPassage> search(String query, int maxResults) {
        if (passages.isEmpty() || maxResults < 1) {
            return List.of();
        }
        Embedding q = model.embed(query).content();
        // shortcut: scans every passage, fine for a few thousand; use an embedding store beyond that
        return IntStream.range(0, passages.size())
                .mapToObj(i -> new RetrievedPassage(passages.get(i).text(), passages.get(i).source(),
                        Math.round(CosineSimilarity.between(q, embeddings.get(i)) * 1000) / 1000.0))
                .filter(r -> r.score() >= minScore)
                .sorted(Comparator.comparingDouble(RetrievedPassage::score).reversed()
                        .thenComparing(RetrievedPassage::source))
                .limit(maxResults)
                .toList();
    }
}
