package tracewise.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The real bge-small-en-v1.5 model over the real corpus, with the minimum score the application uses. */
class FintracRetrievalTest {

    private static EmbeddingRetriever retriever;

    @BeforeAll
    static void index() {
        retriever = new EmbeddingRetriever(new BgeSmallEnV15QuantizedEmbeddingModel(), EmbeddingRetriever.MIN_SCORE);
        retriever.index(IndicatorCorpus.load());
    }

    @Test
    void aPatternInFintracWordsFindsTheMatchingIndicatorFirst() {
        assertEquals("Multiple transactions conducted below the reporting threshold within a short period.",
                retriever.search("deposits below the reporting threshold", 3).get(0).text());
        assertEquals("Funds transferred in and out of an account on the same day or within a relatively short period of time.",
                retriever.search("funds transferred in and out of an account within a short period", 3).get(0).text());
    }

    @Test
    void anUnrelatedQueryFindsNothing() {
        assertEquals(List.of(), retriever.search("weather in Toronto", 3));
    }
}
