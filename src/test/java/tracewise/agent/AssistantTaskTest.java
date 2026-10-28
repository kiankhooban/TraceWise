package tracewise.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AssistantTaskTest {

    static final String ANSWER = """
            C1037 received four cash deposits just under the threshold.
            ```json
            {"kind": "ANSWER", "answer": "C1037 received four cash deposits under 10,000 CAD, totalling 37065.53 CAD.",
             "cited_transactions": [{"transaction_id": "TX-00121", "account_id": "C1037", "amount_cad": 8673.80,
                                     "date": "2026-09-02"}],
             "totals": [{"description": "Four deposits", "amount_cad": 37065.53,
                         "transaction_ids": ["TX-00121", "TX-00183", "TX-00186", "TX-00251"]}]}
            ```""";

    @Test
    void offersReadToolsOnly() {
        var task = new AssistantTask("Why is C1037 risky?", AssistantContext.NONE, List.of());

        assertEquals(Set.of("account_history", "window_aggregates", "counterparties", "linked_alerts", "search_indicators", "similar_cases",
                "query_cases", "query_alerts", "get_case"), task.toolNames());
    }

    @Test
    void theOpeningMessageCarriesHistoryContextAndQuestion() {
        var task = new AssistantTask("And its counterparties?", new AssistantContext("CAS-00001", "C1037"),
                List.of("Q: Why is C1037 risky?\nA: Structuring."));
        String m = task.initialMessage();

        assertTrue(m.indexOf("Why is C1037 risky?") < m.indexOf("And its counterparties?"), m);
        assertTrue(m.contains("CAS-00001") && m.contains("C1037"), m);
        assertEquals("Question: Why is C1037 risky?", new AssistantTask("Why is C1037 risky?", AssistantContext.NONE,
                List.of()).initialMessage());
    }

    @Test
    void thePromptCoversTheDesignedCases() {
        String p = new AssistantTask("q", AssistantContext.NONE, List.of()).systemPrompt();

        for (String expected : List.of("query_cases", "clarifying question", "decline", "instructions", "\"answer\"",
                "CLARIFY", "DECLINE")) {
            assertTrue(p.contains(expected), expected + " missing");
        }
    }

    @Test
    void parsesTheAnswerWithItsClaims() {
        var task = new AssistantTask("q", AssistantContext.NONE, List.of());
        assertNull(task.outputProblem(ANSWER));

        AssistantAnswer a = task.parseAnswer(ANSWER, null);

        assertTrue(a.text().startsWith("C1037 received four cash deposits"));
        assertEquals(AssistantAnswer.Kind.ANSWER, a.kind());
        assertEquals(List.of(new Claim("TX-00121", "C1037", new BigDecimal("8673.80"), LocalDate.parse("2026-09-02"))),
                a.claims());
        assertEquals(new BigDecimal("37065.53"), a.totals().get(0).amountCad());
    }

    @Test
    void aClarifyingQuestionNeedsNoCitations() {
        var task = new AssistantTask("show the big ones", AssistantContext.NONE, List.of());
        String text = "```json\n{\"kind\": \"CLARIFY\", \"answer\": \"Big by amount or by risk score?\", "
                + "\"cited_transactions\": []}\n```";

        assertNull(task.outputProblem(text));
        assertEquals(List.of(), task.parseAnswer(text, null).claims());
        assertEquals(AssistantAnswer.Kind.CLARIFY, task.parseAnswer(text, null).kind());
    }

    @Test
    void aMissingAnswerIsAFormatProblem() {
        var task = new AssistantTask("q", AssistantContext.NONE, List.of());

        assertNotNull(task.outputProblem("```json\n{\"kind\": \"ANSWER\", \"cited_transactions\": []}\n```"));
        assertNotNull(task.outputProblem("```json\n{\"answer\": \"a\", \"cited_transactions\": []}\n```"));
        assertNotNull(task.outputProblem("```json\n{\"kind\": \"MAYBE\", \"answer\": \"a\", \"cited_transactions\": []}\n```"));
        assertNotNull(task.outputProblem("Just prose."));
    }
}
