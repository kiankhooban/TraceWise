package tracewise.retrieval;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.List;

/** A fast stand-in for the real model: one dimension per vocabulary word, counting its occurrences. */
class WordCountEmbeddingModel implements EmbeddingModel {

    private static final List<String> WORDS = List.of("cash", "deposit", "threshold", "wire", "transfer", "payroll");

    int calls;

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        calls++;
        return Response.from(segments.stream().map(s -> vector(s.text().toLowerCase())).toList());
    }

    private static Embedding vector(String text) {
        float[] v = new float[WORDS.size()];
        for (int i = 0; i < v.length; i++) {
            v[i] = text.split(WORDS.get(i), -1).length - 1;
        }
        return Embedding.from(v);
    }
}
