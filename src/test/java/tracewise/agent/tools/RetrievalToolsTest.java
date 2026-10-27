package tracewise.agent.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.agent.AgentFinding;
import tracewise.agent.AgentOutcome;
import tracewise.agent.AgentTrace;
import tracewise.agent.TraceStep;
import tracewise.agent.OutcomeStatus;
import tracewise.agent.Verdict;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Case;
import tracewise.llm.ModelConfig;
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
    void theIndicatorsASearchReturnedAreReadBackFromTheTrace() {
        Retriever retriever = (q, n) -> List.of(new RetrievedPassage("Returned one.", "FINTRAC 7.5, s7", 0.83));
        var ok = searchIndicators(retriever, "{\"query\":\"x\"}");
        var steps = List.of(
                new TraceStep("ALR-1", 1, SearchIndicatorsTool.NAME, "{}", ok.content(), false, 1),
                new TraceStep("ALR-1", 1, SearchIndicatorsTool.NAME, "{}", "search_indicators failed: x", true, 1),
                new TraceStep("ALR-1", 1, "account_history", "{}", "{\"indicators\":[{\"indicator\":\"No.\"}]}", false, 1));

        assertEquals(java.util.Set.of("Returned one."), SearchIndicatorsTool.returnedIndicators(steps));
    }

    @Test
    void theEvidenceOfARunIsReadBackWithSourcesAndScores() {
        var indicators = searchIndicators((q, n) -> List.of(new RetrievedPassage("Returned one.", "FINTRAC 7.5, s7", 0.83)),
                "{\"query\":\"x\"}");
        var steps = List.of(new TraceStep("ALR-1", 1, SearchIndicatorsTool.NAME, "{}", indicators.content(), false, 1),
                new TraceStep("ALR-1", 2, SearchIndicatorsTool.NAME, "{}", indicators.content(), false, 1),
                new TraceStep("ALR-1", 2, SimilarCasesTool.NAME, "{}",
                        "{\"description\":\"d\",\"cases\":[{\"case_id\":\"CAS-00007\",\"summary\":\"CLOSED. Payroll.\","
                                + "\"score\":0.81}]}", false, 1));

        assertEquals(List.of(new RetrievedPassage("Returned one.", "FINTRAC 7.5, s7", 0.83)),
                SearchIndicatorsTool.retrieved(steps), "each indicator once");
        assertEquals(List.of(new RetrievedPassage("CLOSED. Payroll.", "CAS-00007", 0.81)), SimilarCasesTool.retrieved(steps));
    }

    @Test
    void anIndicatorFoundTwiceKeepsItsBestScoreInEitherOrder() {
        var low = searchIndicators((q, n) -> List.of(new RetrievedPassage("Same.", "FINTRAC 7.5, s7", 0.72)), "{\"query\":\"a\"}");
        var high = searchIndicators((q, n) -> List.of(new RetrievedPassage("Same.", "FINTRAC 7.5, s7", 0.91)), "{\"query\":\"b\"}");
        var lowStep = new TraceStep("ALR-1", 1, SearchIndicatorsTool.NAME, "{}", low.content(), false, 1);
        var highStep = new TraceStep("ALR-1", 2, SearchIndicatorsTool.NAME, "{}", high.content(), false, 1);

        assertEquals(0.91, SearchIndicatorsTool.retrieved(List.of(lowStep, highStep)).get(0).score());
        assertEquals(0.91, SearchIndicatorsTool.retrieved(List.of(highStep, lowStep)).get(0).score());
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
        var cases = new SqliteCaseRepository(db);
        var memory = new CaseMemory(cases, () -> new EmbeddingRetriever(new WordCountEmbeddingModel(), 0.5));
        assertEquals(0, json(call(null, memory, SimilarCasesTool.NAME, "{\"description\":\"payroll\"}"))
                .get("cases").size());
        var trace = new AgentTrace("ALR-1", ModelConfig.of("anthropic", "claude-haiku-5-5", false), BigDecimal.ONE);
        trace.finish(new AgentOutcome("ALR-1", OutcomeStatus.COMPLETED, "{}"));
        var finding = new AgentFinding(Verdict.DISMISS, 0.9, List.of(), List.of(), List.of(), "Regular payroll.", false, trace);
        var decided = new Case("CAS-00007", Instant.parse("2026-09-01T00:00:00Z"), List.of("ALR-1"), CaseStatus.CLOSED,
                List.of(finding));
        cases.save(decided);
        memory.remember(decided);

        var found = json(call(null, memory, SimilarCasesTool.NAME, "{\"description\":\"payroll\"}")).get("cases");

        assertEquals("CAS-00007", found.get(0).get("case_id").asText());
        assertEquals("CLOSED. Regular payroll.", found.get(0).get("summary").asText());
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
