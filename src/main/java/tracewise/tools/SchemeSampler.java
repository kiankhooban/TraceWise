package tracewise.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * Samples labelled AML data the way the Stage 1 design says: whole laundering schemes with the full history of every
 * account in them, plus random other accounts with their full history. A slice of rows would break the schemes apart.
 * IBM's schemes come from its patterns file; SAML-D has none, so a scheme there is a group of accounts joined by
 * laundering transactions. A later part skips every scheme or account that is in an earlier part or traded with one,
 * so part 2 shares no row and no chosen account with part 1: tune on part 1, report on part 2. The laundering label
 * stays in the file for F12; the agent never reads it.
 *
 * <p>Usage: {@code SchemeSampler ibm <HI-Small_Trans.csv> <HI-Small_Patterns.txt> <out.csv> <schemes> <otherAccounts>
 * <part> <seed>} or {@code SchemeSampler samld <SAML-D.csv> <out.csv> <schemes> <otherAccounts> <part> <seed>}
 */
public final class SchemeSampler {

    private static final String BEGIN = "BEGIN LAUNDERING ATTEMPT - ";

    private SchemeSampler() {
    }

    public record Sample(Path file, List<String> schemeTypes, Set<String> schemeAccounts, Set<String> otherAccounts,
            int rows) {
    }

    private record Scheme(String type, Set<String> accounts) {
    }

    /** Which columns hold the sender and the receiver account. */
    private record Layout(int from, int to) {
    }

    private static final Layout IBM = new Layout(2, 4);
    private static final Layout SAMLD = new Layout(2, 3);

    public static Sample sampleIbm(Path transactions, Path patterns, Path out, int schemes, int others, int part,
            long seed) throws IOException {
        return sample(transactions, ibmSchemes(patterns), IBM, out, schemes, others, part, seed);
    }

    public static Sample sampleSamlD(Path transactions, Path out, int schemes, int others, int part, long seed)
            throws IOException {
        return sample(transactions, launderingGroups(transactions), SAMLD, out, schemes, others, part, seed);
    }

    private static Sample sample(Path transactions, List<Scheme> all, Layout layout, Path out, int schemes, int others,
            int part, long seed) throws IOException {
        if (schemes < 0 || others < 0 || part < 1) {
            throw new IllegalArgumentException("schemes and other accounts cannot be negative, and parts start at 1");
        }
        var random = new Random(seed);
        var order = new ArrayList<>(all);
        Collections.shuffle(order, random);
        Set<String> planted = new HashSet<>();
        all.forEach(s -> planted.addAll(s.accounts()));
        var pool = new ArrayList<>(accounts(transactions, layout, planted));
        Collections.shuffle(pool, random);

        // parts are taken in turn; a later part skips anything in or trading with an earlier part, so a held-out
        // part shares no row and no chosen account with the parts before it (IBM schemes share accounts)
        Set<String> blocked = new HashSet<>();
        int nextScheme = 0;
        int nextOther = 0;
        List<Scheme> chosen = List.of();
        Set<String> otherAccounts = Set.of();
        for (int p = 1; p <= part; p++) {
            chosen = new ArrayList<>();
            while (chosen.size() < schemes) {
                if (nextScheme >= order.size()) {
                    throw new IllegalArgumentException("There are " + all.size() + " schemes; part " + part + " needs "
                            + schemes + " per part that share no account or transfer with earlier parts");
                }
                Scheme candidate = order.get(nextScheme++);
                if (Collections.disjoint(candidate.accounts(), blocked)) {
                    chosen.add(candidate);
                }
            }
            otherAccounts = new LinkedHashSet<>();
            while (otherAccounts.size() < others) {
                if (nextOther >= pool.size()) {
                    throw new IllegalArgumentException("There are " + pool.size() + " other accounts; part " + part
                            + " needs " + others + " per part away from earlier parts");
                }
                String candidate = pool.get(nextOther++);
                if (!blocked.contains(candidate)) {
                    otherAccounts.add(candidate);
                }
            }
            if (p < part) {
                var taken = new HashSet<>(otherAccounts);
                chosen.forEach(sc -> taken.addAll(sc.accounts()));
                blocked.addAll(taken);
                blocked.addAll(counterparties(transactions, layout, taken));
            }
        }

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
                if (f.length > layout.to() && (keep.contains(f[layout.from()]) || keep.contains(f[layout.to()]))) {
                    w.write(line);
                    w.newLine();
                    rows++;
                }
            }
        }
        return new Sample(out, chosen.stream().map(Scheme::type).toList(), schemeAccounts, otherAccounts, rows);
    }

    /** IBM: each attempt between its BEGIN and END lines, with the accounts its transactions touch. */
    private static List<Scheme> ibmSchemes(Path patterns) throws IOException {
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
                current.accounts().add(f[IBM.from()]);
                current.accounts().add(f[IBM.to()]);
            }
        }
        return result;
    }

    /**
     * SAML-D: accounts joined by laundering transactions (column Is_laundering), one scheme per connected group, named
     * by the typology of its first laundering transaction; groups in first-seen order so a seed gives one sample.
     */
    private static List<Scheme> launderingGroups(Path transactions) throws IOException {
        Map<String, String> parent = new HashMap<>();
        Map<String, String> typeOf = new HashMap<>();
        try (var lines = Files.lines(transactions, StandardCharsets.UTF_8)) {
            lines.skip(1).forEach(line -> {
                String[] f = line.split(",", -1);
                if (f.length > 11 && "1".equals(f[10])) {
                    String a = root(parent, f[SAMLD.from()]);
                    String b = root(parent, f[SAMLD.to()]);
                    parent.put(a, b);
                    typeOf.putIfAbsent(f[SAMLD.from()], f[11]);
                }
            });
        }
        var groups = new LinkedHashMap<String, Scheme>();
        for (String account : parent.keySet().stream().sorted().toList()) {
            String r = root(parent, account);
            groups.computeIfAbsent(r, k -> new Scheme("?", new TreeSet<>())).accounts().add(account);
        }
        return groups.values().stream().map(g -> new Scheme(g.accounts().stream().map(typeOf::get)
                .filter(t -> t != null).findFirst().orElse("?"), g.accounts())).toList();
    }

    private static String root(Map<String, String> parent, String x) {
        parent.putIfAbsent(x, x);
        while (!parent.get(x).equals(x)) {
            parent.put(x, parent.get(parent.get(x)));
            x = parent.get(x);
        }
        return x;
    }

    /** Every account in the file outside the planted schemes, in first-seen order so a seed always gives one sample. */
    private static Set<String> accounts(Path transactions, Layout layout, Set<String> planted) throws IOException {
        var result = new LinkedHashSet<String>();
        try (var lines = Files.lines(transactions, StandardCharsets.UTF_8)) {
            lines.skip(1).forEach(line -> {
                String[] f = line.split(",", -1);
                for (int c : new int[] {layout.from(), layout.to()}) {
                    if (f.length > layout.to() && !planted.contains(f[c])) {
                        result.add(f[c]);
                    }
                }
            });
        }
        return result;
    }

    /** Every account that sent money to or received money from one of these accounts. */
    private static Set<String> counterparties(Path transactions, Layout layout, Set<String> accounts)
            throws IOException {
        var result = new HashSet<String>();
        try (var lines = Files.lines(transactions, StandardCharsets.UTF_8)) {
            lines.skip(1).forEach(line -> {
                String[] f = line.split(",", -1);
                if (f.length > layout.to() && accounts.contains(f[layout.from()])) {
                    result.add(f[layout.to()]);
                }
                if (f.length > layout.to() && accounts.contains(f[layout.to()])) {
                    result.add(f[layout.from()]);
                }
            });
        }
        return result;
    }

    public static void main(String[] args) throws IOException {
        Sample s;
        if (args.length == 8 && args[0].equals("ibm")) {
            s = sampleIbm(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]), Integer.parseInt(args[4]),
                    Integer.parseInt(args[5]), Integer.parseInt(args[6]), Long.parseLong(args[7]));
        } else if (args.length == 7 && args[0].equals("samld")) {
            s = sampleSamlD(Path.of(args[1]), Path.of(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]),
                    Integer.parseInt(args[5]), Long.parseLong(args[6]));
        } else {
            System.err.println("Usage: SchemeSampler ibm <HI-Small_Trans.csv> <HI-Small_Patterns.txt> <out.csv> "
                    + "<schemes> <otherAccounts> <part> <seed>\n   or: SchemeSampler samld <SAML-D.csv> <out.csv> "
                    + "<schemes> <otherAccounts> <part> <seed>");
            System.exit(2);
            return;
        }
        var types = new java.util.TreeMap<String, Integer>();
        s.schemeTypes().forEach(t -> types.merge(t, 1, Integer::sum));
        System.out.println(s.schemeTypes().size() + " schemes " + types + ", " + s.schemeAccounts().size()
                + " scheme accounts, " + s.otherAccounts().size() + " other accounts: " + s.rows() + " rows written to "
                + s.file());
    }
}
