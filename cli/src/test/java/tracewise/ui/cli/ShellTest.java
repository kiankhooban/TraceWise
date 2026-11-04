package tracewise.ui.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import tracewise.tools.ScenarioGenerator;

/** The interactive shell: a command per line, or a question in plain words for the assistant (F10). */
class ShellTest {

    @TempDir Path dir;

    @Test
    void aLineIsACommandOrAQuestion() {
        var commands = Set.of("alerts", "ask", "investigate", "rules");
        assertArrayEquals(new String[] {"alerts", "list"}, TraceWiseShell.arguments(List.of("alerts", "list"), "alerts list", commands));
        assertArrayEquals(new String[] {"ask", "why is C1037 risky?"},
                TraceWiseShell.arguments(List.of("why", "is", "C1037", "risky?"), "  why is C1037 risky? ", commands));
        assertTrue(TraceWiseShell.rootOptions(List.of("--role", "supervisor", "rules", "list")));
        assertTrue(TraceWiseShell.rootOptions(List.of("--db", "other.db")));
        assertFalse(TraceWiseShell.rootOptions(List.of("rules", "list")));
        assertNull(TraceWiseShell.arguments(List.of(), "  ", commands), "an empty line does nothing");
    }

    /** Found by Kian: five pasted lines arrive as one, and ran as one command with every word as its arguments. */
    @Test
    void pastedLinesRunOneAfterAnother() {
        assertEquals(List.of("investigate ALR-00001", "triage", "Why is account C1037 risky?", "exit"),
                TraceWiseShell.lines("investigate ALR-00001\ntriage\r\n\n  Why is account C1037 risky?\nexit\n"));
        assertEquals(List.of("alerts list"), TraceWiseShell.lines("alerts list"));
    }

    String session(String... lines) throws Exception {
        Path data = Files.write(dir.resolve("s.csv"), ScenarioGenerator.generate(42));
        var in = new ByteArrayInputStream((String.join("\n", lines).replace("DATA", data.toString()) + "\n")
                .getBytes(StandardCharsets.UTF_8));
        var bytes = new ByteArrayOutputStream();
        var cl = TraceWiseCli.commandLine(new java.io.PrintWriter(bytes, true), new java.io.PrintWriter(bytes, true));
        cl.parseArgs("--db", dir.resolve("t.db").toString(), "--llm", "replay", "--model", "claude-haiku-5-5");
        try (var terminal = new DumbTerminal("test", "dumb", in, bytes, StandardCharsets.UTF_8)) {
            assertEquals(0, TraceWiseShell.run((TraceWiseCli) cl.getCommand(), cl, terminal, null));
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void commandsRunOneAfterAnotherOnTheSameData() throws Exception {
        String out = session("import DATA", "monitor run", "alerts list --rule R3", "alerts list", "exit");

        assertTrue(out.contains("TRACEWISE"), "the title bar: " + out);
        assertTrue(out.matches("(?s).*Imported +\\d+ of \\d+ rows.*"), out);
        List<String> runs = List.of(out.split("tracewise> "));
        assertTrue(runs.size() >= 5, "the title, then one output per command: " + out);
        String r3 = runs.get(3), all = runs.get(4); // the terminal does not echo what is typed
        assertTrue(r3.contains("R3 Structuring") && !r3.contains("R4 Rapid"), "only R3: " + r3);
        assertTrue(all.contains("R4 Rapid pass-through"), "the next line starts from the defaults again: " + all);
    }

    @Test
    void plainWordsGoToTheAssistantAndHelpListsTheCommands() throws Exception {
        String out = session("help", "which cases are open?");

        assertTrue(out.contains("investigate") && out.contains("approvals"), out);
        assertTrue(out.contains("The assistant could not answer"), "the question went to ask: " + out);
    }

    /** Review 13: --role on a shell line was silently ignored (the session's facade is open), and an options-only line opened a nested shell. */
    @Test
    void rootOptionsOnALineAreRefusedAndTheShellCarriesOn() throws Exception {
        String out = session("--role supervisor", "--db other.db import DATA", "rules list", "exit");
        assertEquals(2, out.split("apply to the whole shell", -1).length - 1, out);
        assertTrue(out.contains("Large cash"), "the next line still runs: " + out);
        assertFalse(Files.exists(dir.resolve("other.db")), "nothing ran against another database");
    }

    /** Review 13: "investigate --json ALR-1" cancelled "--json", and Ctrl+C on other commands did nothing visible. */
    @Test
    void ctrlCStopsTheAlertBeingInvestigatedOrSaysItCannotStop() {
        assertEquals("ALR-00001", TraceWiseShell.alertToStop(new String[] {"investigate", "--json", "ALR-00001"}));
        assertEquals("ALR-00002", TraceWiseShell.alertToStop(new String[] {"investigate", "ALR-00002"}));
        assertNull(TraceWiseShell.alertToStop(new String[] {"triage", "--max-alerts", "5"}));
        assertNull(TraceWiseShell.alertToStop(new String[] {"ask", "why"}));

        var cl = TraceWiseCli.commandLine(new java.io.PrintWriter(new java.io.StringWriter()), new java.io.PrintWriter(new java.io.StringWriter()));
        cl.parseArgs("--db", dir.resolve("t.db").toString());
        var root = (TraceWiseCli) cl.getCommand();
        var text = new java.io.StringWriter();
        var out = new java.io.PrintWriter(text, true);
        TraceWiseShell.stop(root, null, out);
        assertEquals("", text.toString(), "at the prompt Ctrl+C only clears the line");
        TraceWiseShell.stop(root, new String[] {"ask", "why"}, out);
        assertTrue(text.toString().contains("cannot be stopped"), text.toString());
        TraceWiseShell.stop(root, new String[] {"triage"}, out);
        assertTrue(text.toString().contains("Stopping after this alert"), text.toString());
        root.close();
    }

    /** Review 13: the pasted-lines path itself, not only the line splitting. */
    @Test
    void pastedLinesEchoEachLineAndStopAtExit() throws Exception {
        Path data = Files.write(dir.resolve("s.csv"), ScenarioGenerator.generate(42));
        var text = new java.io.StringWriter();
        var out = new java.io.PrintWriter(text, true);
        var cl = TraceWiseCli.commandLine(out, out);
        cl.parseArgs("--db", dir.resolve("t.db").toString());
        var root = (TraceWiseCli) cl.getCommand();
        try (var terminal = new DumbTerminal("test", "dumb", new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(),
                StandardCharsets.UTF_8)) {
            boolean more = TraceWiseShell.runAll(List.of("import " + data, "alerts list", "exit", "rules list"),
                    new org.jline.reader.impl.DefaultParser(), cl, root, terminal, false, new String[1][], out);
            assertFalse(more, "exit ends the shell");
        }
        root.close();
        assertTrue(text.toString().contains("> import ") && text.toString().contains("> alerts list"), text.toString());
        assertFalse(text.toString().contains("Large cash"), "rules list came after exit: " + text);
    }

    /** Review 13: the help line said "investigate --help", which subcommands reject. */
    @Test
    void helpNamesACommandToShowItsOptions() throws Exception {
        String out = session("help", "help investigate", "help nope");
        assertTrue(out.contains("help <command> shows its options"), out);
        assertTrue(out.contains("Usage: tracewise investigate"), out);
        assertTrue(out.contains("No command nope"), out);
    }

    /** Review 13: inside the shell the hint named the default database, not the one the shell opened. */
    @Test
    void theImportHintNamesTheDatabaseTheShellOpened() throws Exception {
        String out = session("import DATA", "import DATA", "exit");
        assertTrue(out.contains("already in " + dir.resolve("t.db")), out);
    }

    @Test
    void withoutATerminalTheBareCommandStillPrintsUsage() {
        var err = new java.io.StringWriter();
        CommandLine cl = TraceWiseCli.commandLine(new java.io.PrintWriter(new java.io.StringWriter()), new java.io.PrintWriter(err, true));
        assertEquals(2, cl.execute("--db", dir.resolve("t.db").toString()));
        assertTrue(err.toString().contains("Usage: tracewise"), err.toString());
    }
}
