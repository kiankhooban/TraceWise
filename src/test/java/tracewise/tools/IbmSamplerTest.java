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

    /**
     * Found on the real file: parts 1 and 2 shared 603 rows, because IBM schemes share accounts and a part-2 account
     * can trade with a part-1 account. A held-out part must share no row and no chosen account with the parts before.
     */
    @Test
    void aHeldOutPartSharesNoRowAndNoChosenAccountWithTheTuningPart() throws Exception {
        transactions = Files.write(dir.resolve("t2.csv"), List.of(HEADER, row("A", "B", 1), row("A", "C", 1),
                row("C", "D", 1), row("D", "C", 1), row("E", "F", 1), row("G", "F", 1), row("H", "I", 1),
                row("J", "K", 1), row("L", "M", 1), row("N", "O", 0), row("O", "P", 0), row("P", "Q", 0),
                row("Q", "R", 0), row("R", "S", 0), row("S", "N", 0), row("B", "N", 0), row("F", "H", 0),
                row("K", "P", 0)));
        patterns = Files.write(dir.resolve("p2.txt"), List.of(
                "BEGIN LAUNDERING ATTEMPT - FAN-OUT:  x", row("A", "B", 1), row("A", "C", 1), "END LAUNDERING ATTEMPT",
                "BEGIN LAUNDERING ATTEMPT - CYCLE:  x", row("C", "D", 1), row("D", "C", 1), "END LAUNDERING ATTEMPT",
                "BEGIN LAUNDERING ATTEMPT - FAN-IN:  x", row("E", "F", 1), row("G", "F", 1), "END LAUNDERING ATTEMPT",
                "BEGIN LAUNDERING ATTEMPT - STACK:  x", row("H", "I", 1), "END LAUNDERING ATTEMPT",
                "BEGIN LAUNDERING ATTEMPT - RANDOM:  x", row("J", "K", 1), "END LAUNDERING ATTEMPT",
                "BEGIN LAUNDERING ATTEMPT - BIPARTITE:  x", row("L", "M", 1), "END LAUNDERING ATTEMPT"));
        int checked = 0;
        for (long seed = 1; seed <= 40; seed++) {
            IbmSampler.Sample one;
            IbmSampler.Sample two;
            try {
                one = IbmSampler.sample(transactions, patterns, dir.resolve("a" + seed), 1, 1, 1, seed);
                two = IbmSampler.sample(transactions, patterns, dir.resolve("b" + seed), 1, 1, 2, seed);
            } catch (IllegalArgumentException e) {
                continue; // this order ran out of candidates far enough from part 1
            }
            checked++;
            var rowsOne = new HashSet<>(rows(one.file()).subList(1, rows(one.file()).size()));
            var rowsTwo = rows(two.file()).subList(1, rows(two.file()).size());
            assertTrue(Collections.disjoint(rowsOne, rowsTwo), "seed " + seed);
            var chosenTwo = new HashSet<>(two.schemeAccounts());
            chosenTwo.addAll(two.otherAccounts());
            for (String line : rowsOne) {
                String[] f = line.split(",");
                assertFalse(chosenTwo.contains(f[2]) || chosenTwo.contains(f[4]), "seed " + seed + ": " + line);
            }
        }
        assertTrue(checked >= 20, "only " + checked + " seeds could be checked");
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
