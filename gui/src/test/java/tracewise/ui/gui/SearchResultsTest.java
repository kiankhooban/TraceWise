package tracewise.ui.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import tracewise.agent.AssistantContext;
import tracewise.agent.TraceStep;

/** F10: "search results open as a table" (Stage 1), from the query tool the assistant ran. */
class SearchResultsTest {

    static TraceStep step(String tool, String result, boolean error) {
        return new TraceStep("assistant", 1, tool, "{}", result, error, 5);
    }

    static final String ALERTS = """
            {"count":2,"alerts":[{"alert_id":"ALR-00001","rule":"R3","total_cad":"37065.53","transaction_ids":["TX-1","TX-2"],"case_id":"CAS-00001"},
            {"alert_id":"ALR-00003","rule":"R5","total_cad":"12208.90","transaction_ids":[],"case_id":null}]}""";

    @Test
    void theLastSearchTheAssistantRanBecomesATable() {
        var r = SearchResults.from(List.of(step("query_cases", "{\"count\":0,\"cases\":[]}", false),
                step("account_history", "{}", false), step("query_alerts", ALERTS, false))).orElseThrow();
        assertEquals("2 alerts", r.title());
        assertEquals(List.of("alert id", "rule", "total cad", "transaction ids", "case id"), r.columns());
        assertEquals(List.of("ALR-00001", "R3", "37065.53", "TX-1 TX-2", "CAS-00001"), r.rows().get(0));
        assertEquals(List.of("ALR-00003", "R5", "12208.90", "", ""), r.rows().get(1));
    }

    @Test
    void noSearchOrAFailedOneGivesNoTable() {
        assertTrue(SearchResults.from(List.of(step("account_history", "{}", false))).isEmpty());
        assertTrue(SearchResults.from(List.of(step("query_alerts", "'status' must be NEW", true))).isEmpty());
    }

    @Test
    void anEmptySearchStillSaysWhatWasFound() {
        var r = SearchResults.from(List.of(step("query_cases", "{\"count\":0,\"cases\":[]}", false))).orElseThrow();
        assertEquals("0 cases", r.title());
        assertTrue(r.rows().isEmpty());
    }

    @Test
    void aCasesAlertsReadAsTheirIds() {
        var r = SearchResults.from(List.of(step("query_cases", """
                {"count":1,"cases":[{"case_id":"CAS-00001","status":"PENDING_APPROVAL","alerts":[{"alert_id":"ALR-00001","rule":"R3"},
                {"alert_id":"ALR-00004","rule":"R6"}]}]}""", false))).orElseThrow();
        assertEquals(List.of("CAS-00001", "PENDING_APPROVAL", "ALR-00001 ALR-00004"), r.rows().get(0));
        assertEquals("1 case", r.title());
    }

    @Test
    void aCappedSearchSaysHowManyItShows() {
        var r = SearchResults.from(List.of(step("query_alerts", """
                {"count":120,"alerts":[{"alert_id":"ALR-00001"}],"message":"Only the first 50 are listed; narrow the filter."}""",
                false))).orElseThrow();
        assertEquals("1 of 120 alerts. Only the first 50 are listed; narrow the filter.", r.title());
    }

    @Test
    void theContextSentWithAQuestionIsShownInWords() {
        assertEquals("context CAS-00001", SearchResults.contextLabel(new AssistantContext("CAS-00001", null)));
        assertEquals("context account C1059", SearchResults.contextLabel(new AssistantContext(null, "C1059")));
        assertEquals("no context", SearchResults.contextLabel(AssistantContext.NONE));
    }
}
