package tracewise.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * Samples IBM's synthetic AML data (HI-Small) the way the Stage 1 design says: whole planted schemes from the patterns
 * file with the full history of every account in them, plus random other accounts with their full history. A slice
 * of rows would break the schemes apart. Part 1 and part 2 share no scheme and no other account, so one can be used to
 * tune and the other, held out, to report. The laundering label stays in the file for F12; the agent never reads it.
 *
 * <p>Usage: {@code IbmSampler <HI-Small_Trans.csv> <HI-Small_Patterns.txt> <out.csv> <schemes> <otherAccounts> <part>
 * <seed>}
 */
public final class IbmSampler {

    /** The two columns named "Account": sender and receiver. */
    private static final int FROM = 2;
    private static final int TO = 4;
    private static final String BEGIN = "BEGIN LAUNDERING ATTEMPT - ";

    private IbmSampler() {
    }

    public record Sample(Path file, List<String> schemeTypes, Set<String> schemeAccounts, Set<String> otherAccounts,
            int rows) {
    }

    private record Scheme(String type, Set<String> accounts) {
    }

    public static Sample sample(Path transactions, Path patterns, Path out, int schemes, int others, int part,
            long seed) throws IOException {
        if (schemes < 0 || others < 0 || part < 1) {
            throw new IllegalArgumentException("schemes and other accounts cannot be negative, and parts start at 1");
        }
        List<Scheme> all = schemes(patterns);
        var random = new Random(seed);
        var order = new ArrayList<>(all);
        Collections.shuffle(order, random);
        List<Scheme> chosen = slice(order, schemes, part, "schemes");

        Set<String> planted = new HashSet<>();
        all.forEach(s -> planted.addAll(s.accounts()));
        var pool = new ArrayList<>(accounts(transactions, planted));
        Collections.shuffle(pool, random);
        Set<String> otherAccounts = new LinkedHashSet<>(slice(pool, others, part, "other accounts"));

        Set<String> schemeAccounts = new TreeSet<>();
        chosen.forEach(s -> schemeAccounts.addAll(s.accounts()));
        Set<String> keep = new HashSet<>(schemeAccounts);
        keep.addAll(otherAccounts);
        int rows = 0;
        try (BufferedReader in = Files.newBufferedReader(transactions, StandardCharsets.UTF_8);
                var w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            w.write(in.readLine());
            w.newLine();
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                String[] f = line.split(",", -1);
                if (f.length > TO && (keep.contains(f[FROM]) || keep.contains(f[TO]))) {
                    w.write(line);
                    w.newLine();
                    rows++;
                }
            }
        }
        return new Sample(out, chosen.stream().map(Scheme::type).toList(), schemeAccounts, otherAccounts, rows);
    }

    /** Part p takes the p-th block of size n, so parts never overlap. */
    private static <T> List<T> slice(List<T> shuffled, int n, int part, String what) {
        int first = (part - 1) * n;
        if (first + n > shuffled.size()) {
            throw new IllegalArgumentException("There are " + shuffled.size() + " " + what + "; part " + part + " of "
                    + n + " needs " + (first + n));
        }
        return shuffled.subList(first, first + n);
    }

    /** Each attempt between its BEGIN and END lines, with the accounts its transactions touch. */
    private static List<Scheme> schemes(Path patterns) throws IOException {
        var result = new ArrayList<Scheme>();
        Scheme current = null;
        for (String line : Files.readAllLines(patterns, StandardCharsets.UTF_8)) {
            if (line.startsWith(BEGIN)) {
                int colon = line.indexOf(':');
                current = new Scheme(line.substring(BEGIN.length(), colon < 0 ? line.length() : colon).strip(),
                        new LinkedHashSet<>());
                result.add(current);
            } else if (line.startsWith("END ")) {
                current = null;
            } else if (current != null && !line.isBlank()) {
                String[] f = line.split(",", -1);
                current.accounts().add(f[FROM]);
                current.accounts().add(f[TO]);
            }
        }
        return result;
    }

    /** Every account in the file outside the planted schemes, in first-seen order so a seed always gives one sample. */
    private static Set<String> accounts(Path transactions, Set<String> planted) throws IOException {
        var result = new LinkedHashSet<String>();
        try (var lines = Files.lines(transactions, StandardCharsets.UTF_8)) {
            lines.skip(1).forEach(line -> {
                String[] f = line.split(",", -1);
                for (int c : new int[] {FROM, TO}) {
                    if (f.length > TO && !planted.contains(f[c])) {
                        result.add(f[c]);
                    }
                }
            });
        }
        return result;
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 7) {
            System.err.println("Usage: IbmSampler <HI-Small_Trans.csv> <HI-Small_Patterns.txt> <out.csv> <schemes> "
                    + "<otherAccounts> <part> <seed>");
            System.exit(2);
        }
        var s = sample(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), Integer.parseInt(args[3]),
                Integer.parseInt(args[4]), Integer.parseInt(args[5]), Long.parseLong(args[6]));
        var types = new java.util.TreeMap<String, Integer>();
        s.schemeTypes().forEach(t -> types.merge(t, 1, Integer::sum));
        System.out.println(s.schemeTypes().size() + " schemes " + types + ", " + s.schemeAccounts().size()
                + " scheme accounts, " + s.otherAccounts().size() + " other accounts: " + s.rows() + " rows written to "
                + s.file());
    }
}
