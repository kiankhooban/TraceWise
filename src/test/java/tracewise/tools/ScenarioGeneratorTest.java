package tracewise.tools;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import tracewise.ingest.CurrencyConverter;
import tracewise.ingest.ScenarioCsvFormat;

class ScenarioGeneratorTest {

    @Test
    void sameSeedGivesIdenticalOutputAndDifferentSeedsDiffer() {
        assertEquals(ScenarioGenerator.generate(42), ScenarioGenerator.generate(42));
        assertNotEquals(ScenarioGenerator.generate(42), ScenarioGenerator.generate(7));
    }

    @Test
    void everyRowParsesInTheScenarioFormat() {
        var format = new ScenarioCsvFormat(CurrencyConverter.bankOfCanadaDefaults());
        List<String> lines = ScenarioGenerator.generate(42);
        assertTrue(format.matches(Arrays.asList(lines.get(0).split(",", -1))));
        for (int i = 1; i < lines.size(); i++) {
            format.parse(Arrays.asList(lines.get(i).split(",", -1)), i + 1);
        }
        assertTrue(lines.size() > 300, "normal customers make up most of the data");
    }

    @Test
    void plantedSchemesAreLabelledAndNormalCustomersAreNot() {
        List<String> lines = ScenarioGenerator.generate(42);
        for (String scheme : List.of("STRUCT", "PASS", "FANIN", "FANOUT")) {
            assertTrue(lines.stream().anyMatch(l -> l.startsWith(scheme + "-") && l.endsWith(",1")), scheme);
        }
        assertTrue(lines.stream().filter(l -> l.startsWith("N-")).allMatch(l -> l.endsWith(",0")));
    }
}
