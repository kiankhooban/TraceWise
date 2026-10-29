package tracewise.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IndicatorCorpusTest {

    @Test
    void loadsEveryFintracIndicatorWithItsSection() {
        var passages = IndicatorCorpus.load();

        assertEquals(110, passages.size());
        var structuring = passages.stream()
                .filter(p -> p.text().equals("Multiple transactions conducted below the reporting threshold within a short period."))
                .findFirst().orElseThrow();
        assertTrue(structuring.source().startsWith("FINTRAC 7."), structuring.source());
        assertTrue(structuring.source().contains("structured below the reporting"), structuring.source());
    }

    @Test
    void aMissingCorpusIsReportedAsUnavailable() {
        var e = assertThrows(IllegalStateException.class, () -> IndicatorCorpus.load("/tracewise/missing.tsv"));
        assertTrue(e.getMessage().contains("unavailable"), e.getMessage());
    }
}
