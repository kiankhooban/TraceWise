package tracewise.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class EmbeddingRetrieverTest {

    private final EmbeddingRetriever retriever = new EmbeddingRetriever(new WordCountEmbeddingModel(), 0.5);

    @Test
    void returnsTheClosestPassagesFirstWithTheirScores() {
        retriever.index(List.of(new Passage("A", "wire transfer abroad"), new Passage("B", "cash deposit under threshold"),
                new Passage("C", "cash deposit")));

        var results = retriever.search("cash deposit below the threshold", 5);

        assertEquals(List.of("B", "C"), results.stream().map(RetrievedPassage::source).toList());
        assertEquals("cash deposit under threshold", results.get(0).text());
        assertEquals(1.0, results.get(0).score(), 1e-6);
        assertTrue(results.get(1).score() < results.get(0).score());
    }

    @Test
    void leavesOutPassagesBelowTheMinimumScore() {
        retriever.index(List.of(new Passage("A", "wire transfer"), new Passage("P", "payroll")));

        assertEquals(List.of(), retriever.search("cash deposit", 5));
    }

    @Test
    void returnsAtMostMaxResults() {
        retriever.index(List.of(new Passage("A", "cash"), new Passage("B", "cash deposit"), new Passage("C", "cash cash")));

        assertEquals(2, retriever.search("cash", 2).size());
    }

    @Test
    void anEmptyIndexFindsNothing() {
        assertEquals(List.of(), retriever.search("cash", 5));
    }

    @Test
    void passagesIndexedLaterAreFoundToo() {
        retriever.index(List.of(new Passage("A", "wire")));
        retriever.index(List.of(new Passage("B", "payroll")));

        assertEquals("B", retriever.search("payroll", 5).get(0).source());
    }
}
