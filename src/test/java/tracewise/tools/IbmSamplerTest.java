package tracewise.tools;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Stage 1 sampling of the IBM data: whole planted schemes with the full history of their accounts, plus random
 * other accounts with theirs, never a slice of rows (which would break the schemes apart).
 */
class IbmSamplerTest {

    static final String HEADER = "Timestamp,From Bank,Account,To Bank,Account,Amount Received,Receiving Currency,"
            + "Amount Paid,Payment Currency,Payment Format,Is Laundering";

    @TempDir Path dir;
    Path transactions;
    Path patterns;

    static String row(String from, String to, int laundering) {
        return "2022/09/01 00:06,001," + from + ",002," + to + ",100.00,US Dollar,100.00,US Dollar,ACH," + laundering;
    }

    @BeforeEach
    void files() throws Exception {
        // three schemes (A pays B and C; D and E cycle; K gathers from L and M), and normal accounts F to J
        transactions = Files.write(dir.resolve("t.csv"), List.of(HEADER, row("A", "B", 1), row("F", "G", 0),
                row("A", "C", 1), row("A", "G", 0), row("D", "E", 1), row("H", "I", 0), row("E", "D", 1),
                row("I", "J", 0), row("L", "K", 1), row("M", "K", 1), row("J", "F", 0)));
        patterns = Files.write(dir.resolve("p.txt"), List.of(
                "BEGIN LAUNDERING ATTEMPT - FAN-OUT:  Max 2-degree Fan-Out", row("A", "B", 1), row("A", "C", 1),
                "END LAUNDERING ATTEMPT - FAN-OUT", "",
                "BEGIN LAUNDERING ATTEMPT - CYCLE:  Max 2 hops", row("D", "E", 1), row("E", "D", 1),
                "END LAUNDERING ATTEMPT - CYCLE", "",
                "BEGIN LAUNDERING ATTEMPT - FAN-IN:  Max 2-degree Fan-In", row("L", "K", 1), row("M", "K", 1),
                "END LAUNDERING ATTEMPT - FAN-IN"));
    }

    IbmSampler.Sample sample(int part, long seed) throws Exception {
        return IbmSampler.sample(transactions, patterns, dir.resolve("out-" + part + "-" + seed + ".csv"), 1, 2, part,
                seed);
    }

    static List<String> rows(Path file) throws Exception {
        return Files.readAllLines(file);
    }

    @Test
    void theSampleKeepsWholeSchemesAndTheFullHistoryOfEveryChosenAccount() throws Exception {
        var s = sample(1, 42);
        List<String> out = rows(s.file());

        assertEquals(HEADER, out.get(0));
        assertEquals(1, s.schemeTypes().size());
        Set<String> chosen = new HashSet<>(s.schemeAccounts());
        chosen.addAll(s.otherAccounts());
        for (String line : rows(transactions).subList(1, 12)) {
            String[] f = line.split(",");
            boolean touches = chosen.contains(f[2]) || chosen.contains(f[4]);
            assertEquals(touches, out.contains(line), line);
        }
        assertEquals(out.size() - 1, s.rows());
    }

    @Test
    void otherAccountsAreNeverPartOfAPlantedScheme() throws Exception {
        for (long seed = 1; seed <= 20; seed++) {
            var s = sample(1, seed);
            assertEquals(2, s.otherAccounts().size());
            assertTrue(Collections.disjoint(s.otherAccounts(), Set.of("A", "B", "C", "D", "E", "K", "L", "M")),
                    s.otherAccounts().toString());
        }
    }

    /** Tune on part 1, report on part 2: they share no scheme and no other account. */
    @Test
    void partsOneAndTwoAreDisjoint() throws Exception {
        var one = sample(1, 42);
        var two = sample(2, 42);

        assertTrue(Collections.disjoint(one.schemeAccounts(), two.schemeAccounts()));
        assertTrue(Collections.disjoint(one.otherAccounts(), two.otherAccounts()));
    }

    @Test
    void theSameSeedGivesTheSameSample() throws Exception {
        assertEquals(rows(sample(1, 42).file()), rows(sample(1, 42).file()));
    }

    @Test
    void askingForMoreSchemesThanThePartHoldsIsRefused() {
        var e = assertThrows(IllegalArgumentException.class, () -> IbmSampler.sample(transactions, patterns,
                dir.resolve("x.csv"), 2, 0, 2, 42));
        assertTrue(e.getMessage().contains("3 schemes"), e.getMessage());
    }
}
