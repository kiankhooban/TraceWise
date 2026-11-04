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
        assertArrayEquals(new String[] {"--role", "supervisor", "rules", "list"},
                TraceWiseShell.arguments(List.of("--role", "supervisor", "rules", "list"), "", commands));
        assertNull(TraceWiseShell.arguments(List.of(), "  ", commands), "an empty line does nothing");
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

    @Test
    void withoutATerminalTheBareCommandStillPrintsUsage() {
        var err = new java.io.StringWriter();
        CommandLine cl = TraceWiseCli.commandLine(new java.io.PrintWriter(new java.io.StringWriter()), new java.io.PrintWriter(err, true));
        assertEquals(2, cl.execute("--db", dir.resolve("t.db").toString()));
        assertTrue(err.toString().contains("Usage: tracewise"), err.toString());
    }
}
