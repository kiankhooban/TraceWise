package tracewise.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.model.embedding.EmbeddingModel;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import tracewise.retrieval.Passage;
import tracewise.retrieval.WordCountEmbeddingModel;

class TraceWiseBootstrapTest {

    int modelsLoaded;

    Supplier<EmbeddingModel> model() {
        return () -> {
            modelsLoaded++;
            return new WordCountEmbeddingModel();
        };
    }

    @Test
    void theIndicatorIndexIsBuiltOnFirstSearchOnly() {
        var retriever = TraceWiseBootstrap.indicatorRetriever(() -> List.of(new Passage("FINTRAC 7.5", "cash deposit")),
                model());
        assertEquals(0, modelsLoaded);

        retriever.search("cash", 3);
        retriever.search("cash", 3);

        assertEquals(1, modelsLoaded);
        assertEquals("FINTRAC 7.5", retriever.search("cash deposit", 3).get(0).source());
    }

    @Test
    void aMissingCorpusMakesSearchFailWithTheLimitation() {
        var retriever = TraceWiseBootstrap.indicatorRetriever(() -> {
            throw new IllegalStateException("Indicator retrieval is unavailable: corpus missing");
        }, model());

        var e = assertThrows(IllegalStateException.class, () -> retriever.search("cash", 3));
        assertTrue(e.getMessage().contains("unavailable"));
        assertEquals(0, modelsLoaded);
    }
}
