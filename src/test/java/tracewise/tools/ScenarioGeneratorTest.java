package tracewise.tools;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
        Map<String, String> ids = ScenarioGenerator.accounts(42);
        for (String role : List.of("S-C1", "I-C2", "P-C1", "F-C1", "O-C1")) {
            assertTrue(lines.stream().anyMatch(l -> l.contains("," + ids.get(role) + ",") && l.endsWith(",1")), role);
        }
        for (int c = 1; c <= 60; c++) {
            String id = ids.get(String.format("N-C%02d", c));
            assertTrue(lines.stream().filter(l -> l.contains("," + id + ",")).allMatch(l -> l.endsWith(",0")), id);
        }
    }

    @Test
    void noIdRevealsWhichSchemeOrCustomerTypeItBelongsTo() {
        List<String> lines = ScenarioGenerator.generate(42);
        for (String line : lines.subList(1, lines.size())) {
            String[] f = line.split(",", -1);
            assertTrue(f[0].matches("TX-\\d{5}"), f[0]);
            for (String account : List.of(f[3], f[5])) {
                assertTrue(account.matches("C\\d{4}|EXT-\\d{4}|CASH"), account);
            }
        }
    }
}
