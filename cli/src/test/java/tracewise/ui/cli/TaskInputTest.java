package tracewise.ui.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tracewise.tools.ScenarioGenerator;

/**
 * The standalone CLI runs a task from a file or standard input (java -jar tracewise.jar --input task.txt): each line
 * is a command, or a question in plain words for the assistant, as in the shell.
 */
class TaskInputTest {

    @TempDir Path dir;
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    int code;

    String run(String task, boolean fromStdin) throws Exception {
        Path data = Files.write(dir.resolve("s.csv"), ScenarioGenerator.generate(42));
        String text = task.replace("DATA", data.toString());
        var cl = TraceWiseCli.commandLine(new PrintWriter(bytes, true), new PrintWriter(bytes, true));
        String input = "-";
        if (fromStdin) {
            ((TraceWiseCli) cl.getCommand()).stdin = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        } else {
            input = Files.writeString(dir.resolve("task.txt"), text).toString();
        }
        code = cl.execute("--db", dir.resolve("t.db").toString(), "--llm", "replay", "--model", "claude-haiku-5-5",
                "--input", input);
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void aTaskFileRunsEachLineInOrderOnTheSameData() throws Exception {
        String out = run("# load the sample data, then run the rules\nimport DATA\n\nmonitor run\nalerts list --rule R3\n",
                false);

        assertEquals(0, code, out);
        assertTrue(Files.exists(dir.resolve("t.db")), "the --db given, not the default database");
        assertTrue(out.matches("(?s).*Imported +(\\d+) of \\1 rows.*"), "every row, into the --db given: " + out);
        assertTrue(out.contains("R3 Structuring"), "the alerts the rules raised: " + out);
        assertTrue(out.contains("> alerts list --rule R3"), "each line is shown before its output: " + out);
        assertFalse(out.contains("load the sample data"), "a # line is a comment: " + out);
    }

    @Test
    void standardInputWhenTheInputIsADash() throws Exception {
        String out = run("import DATA\nmonitor run\nwhich cases are open?\n", true);

        assertTrue(out.matches("(?s).*Imported +(\\d+) of \\1 rows.*"), "into the --db given: " + out);
        assertFalse(out.contains("claude-sonnet"), "the --llm and --model given, not the defaults: " + out);
        assertTrue(out.contains("The assistant could not answer"), "plain words go to the assistant: " + out);
    }

    @Test
    void aFailingLineGivesANonZeroExitCodeAndTheRestStillRuns() throws Exception {
        String out = run("import DATA\nnetwork NOPE\nmonitor run\n", false);

        assertNotEquals(0, code, out);
        assertTrue(out.contains("NOPE"), "the failing line's error: " + out);
        assertTrue(out.contains("> monitor run") && out.contains("threshold"), "the next line still ran: " + out);
    }

    @Test
    void aMissingTaskFileIsAClearError() {
        var cl = TraceWiseCli.commandLine(new PrintWriter(bytes, true), new PrintWriter(bytes, true));
        code = cl.execute("--db", dir.resolve("t.db").toString(), "--input", dir.resolve("none.txt").toString());

        assertEquals(2, code);
        assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("No task file"), bytes.toString(StandardCharsets.UTF_8));
    }
}
