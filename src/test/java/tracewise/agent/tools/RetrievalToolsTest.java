package tracewise.agent.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.agent.AgentFinding;
import tracewise.agent.Verdict;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Case;
import tracewise.llm.ToolCall;
import tracewise.persistence.Database;
import tracewise.persistence.SqliteCaseRepository;
import tracewise.retrieval.CaseMemory;
import tracewise.retrieval.EmbeddingRetriever;
import tracewise.retrieval.RetrievedPassage;
import tracewise.retrieval.Retriever;
import tracewise.retrieval.WordCountEmbeddingModel;

class RetrievalToolsTest {

    @TempDir Path dir;
    final List<String> asked = new ArrayList<>();

    ToolResult call(Retriever retriever, CaseMemory memory, String tool, String args) {
        var registry = new ToolRegistry();
        registry.register(new SearchIndicatorsTool(retriever));
        registry.register(new SimilarCasesTool(memory));
        return registry.execute(new ToolCall("c1", tool, args));
    }

    ToolResult searchIndicators(Retriever retriever, String args) {
        return call(retriever, null, SearchIndicatorsTool.NAME, args);
    }

    static JsonNode json(ToolResult r) throws Exception {
        return new ObjectMapper().readTree(r.content());
    }

    @Test
    void searchIndicatorsReturnsTheMatchesWithTheirSourceAndScore() throws Exception {
        Retriever retriever = (q, n) -> {
            asked.add(q + "|" + n);
            return List.of(new RetrievedPassage("Multiple transactions below the threshold.", "FINTRAC 7.5, s7", 0.83));
        };

        var result = searchIndicators(retriever, "{\"query\":\"deposits below the reporting threshold\"}");

        assertFalse(result.error());
        assertEquals(List.of("deposits below the reporting threshold|5"), asked);
        var first = json(result).get("indicators").get(0);
        assertEquals("Multiple transactions below the threshold.", first.get("indicator").asText());
        assertEquals("FINTRAC 7.5, s7", first.get("source").asText());
        assertEquals(0.83, first.get("score").asDouble());
    }

    @Test
    void searchIndicatorsAsksForAtMostTenResults() {
        searchIndicators((q, n) -> {
            asked.add(String.valueOf(n));
            return List.of();
        }, "{\"query\":\"x\",\"max_results\":50}");

        assertEquals(List.of("10"), asked);
    }

    @Test
    void noMatchSaysExplicitlyThatNothingRelevantWasFound() throws Exception {
        var result = searchIndicators((q, n) -> List.of(), "{\"query\":\"weather in Toronto\"}");

        assertFalse(result.error());
        assertEquals(0, json(result).get("indicators").size());
        assertTrue(json(result).get("message").asText().contains("Do not cite"), result.content());
    }

    @Test
    void anUnavailableCorpusIsAnErrorTheAgentCanContinueFrom() {
        var result = searchIndicators((q, n) -> {
            throw new IllegalStateException("Indicator retrieval is unavailable: corpus missing");
        }, "{\"query\":\"x\"}");

        assertTrue(result.error());
        assertTrue(result.content().contains("unavailable"), result.content());
    }

    @Test
    void similarCasesReturnsPastDecisions() throws Exception {
        var db = Database.open(dir.resolve("t.db"));
        var memory = new CaseMemory(new SqliteCaseRepository(db),
                () -> new EmbeddingRetriever(new WordCountEmbeddingModel(), 0.5));
        assertEquals(0, json(call(null, memory, SimilarCasesTool.NAME, "{\"description\":\"payroll\"}"))
                .get("cases").size());
        var finding = new AgentFinding(Verdict.DISMISS, 0.9, List.of(), List.of(), List.of(), "Regular payroll.", false, null);
        memory.remember(new Case("CAS-00007", Instant.parse("2026-09-01T00:00:00Z"), List.of("ALR-1"), CaseStatus.CLOSED,
                List.of(finding)));

        var cases = json(call(null, memory, SimilarCasesTool.NAME, "{\"description\":\"payroll\"}")).get("cases");

        assertEquals("CAS-00007", cases.get(0).get("case_id").asText());
        assertEquals("CLOSED. Regular payroll.", cases.get(0).get("summary").asText());
        db.close();
    }

    @Test
    void similarCasesSaysWhenThereIsNoPrecedent() throws Exception {
        var db = Database.open(dir.resolve("t.db"));
        var memory = new CaseMemory(new SqliteCaseRepository(db), () -> null);

        var result = json(call(null, memory, SimilarCasesTool.NAME, "{\"description\":\"payroll\"}"));

        assertTrue(result.get("message").asText().contains("No closed or escalated case"), result.toString());
        db.close();
    }
}
