package tracewise.ui.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp;
import picocli.CommandLine;
import picocli.shell.jline3.PicocliCommands;
import tracewise.domain.AlertStatus;
import tracewise.monitoring.AlertFilter;

/**
 * The interactive shell, opened by tracewise with no command. Each line runs one of the same picocli commands, so
 * every result looks as it does from ./tracewise &lt;command&gt;; a line that does not start with a command is a
 * question for the assistant (F10). All lines share one facade, so the assistant keeps the conversation.
 */
final class TraceWiseShell {

    private TraceWiseShell() {
    }

    /** The arguments a line runs: the command as typed, or ask with the whole line; null for an empty line. */
    static String[] arguments(List<String> words, String line, Set<String> commands) {
        if (words.isEmpty()) {
            return null;
        }
        String first = words.get(0);
        return commands.contains(first) || first.startsWith("-") ? words.toArray(String[]::new)
                : new String[] {"ask", line.strip()};
    }

    /**
     * A line that starts with a root option such as --role or --db. The shell's facade was opened with the options the
     * shell started with, so a per-line option would be silently ignored, and an options-only line would open a
     * second shell; such lines are refused. Help and version are not root options in that sense.
     */
    static boolean rootOptions(List<String> words) {
        return !words.isEmpty() && words.get(0).startsWith("-")
                && !Set.of("-h", "--help", "-V", "--version").contains(words.get(0));
    }

    /** The lines in what was read: a paste of several lines arrives at once and runs one line after another. */
    static List<String> lines(String text) {
        return text.lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
    }

    /** Runs lines until exit, quit or Ctrl+D; history is kept in the file, or not at all when it is null. */
    static int run(TraceWiseCli root, CommandLine cl, Terminal terminal, Path history) {
        PrintWriter out = terminal.writer();
        cl.setOut(out);
        cl.setErr(out);
        boolean color = root.style.color;
        var commands = new PicocliCommands(cl).compileCompleters();
        commands.compile();
        var builder = LineReaderBuilder.builder().terminal(terminal).completer(commands).parser(new DefaultParser());
        if (history != null) {
            builder.variable(LineReader.HISTORY_FILE, history);
        }
        LineReader reader = builder.build();
        var parser = new DefaultParser();
        root.shell = true;
        String[][] running = new String[1][];
        // Ctrl+C while the agent works stops it; the shell stays open (at the prompt it clears the line)
        terminal.handle(Terminal.Signal.INT, signal -> stop(root, running[0], out));
        try {
            root.style = new Style(color, width(terminal));
            header(root, out);
            String prompt = color ? "\033[1;38;5;214mtracewise›\033[0m " : "tracewise> ";
            while (true) {
                String read;
                try {
                    read = reader.readLine(prompt);
                } catch (UserInterruptException e) {
                    continue;
                } catch (EndOfFileException e) {
                    break;
                }
                if (!runAll(lines(read), parser, cl, root, terminal, color, running, out)) {
                    break;
                }
            }
        } finally {
            root.shell = false;
            root.close();
        }
        return 0;
    }

    /** Runs each line in order; false once a line is exit or quit, so the lines after it do not run. */
    static boolean runAll(List<String> lines, DefaultParser parser, CommandLine cl, TraceWiseCli root,
            Terminal terminal, boolean color, String[][] running, PrintWriter out) {
        for (String line : lines) {
            var words = new ArrayList<String>(parser.parse(line, line.length()).words());
            words.removeIf(String::isEmpty);
            if (words.size() == 1 && Set.of("exit", "quit").contains(words.get(0))) {
                return false;
            }
            if (rootOptions(words)) {
                out.println(root.style.waiting(root.style.wrap("Root options such as --role and --db apply to the whole "
                        + "shell: exit, then start it with them, for example ./tracewise --role supervisor", 2)));
                continue;
            }
            if (!words.isEmpty() && words.get(0).equals("help") && words.size() <= 2) {
                var sub = words.size() == 2 ? cl.getSubcommands().get(words.get(1)) : null;
                if (words.size() == 1) {
                    help(cl, root.style, out);
                } else if (sub == null) {
                    out.println("  No command " + words.get(1) + "; help lists them");
                } else {
                    sub.usage(out);
                }
                continue;
            }
            if (words.size() == 1 && words.get(0).equals("clear")) {
                terminal.puts(InfoCmp.Capability.clear_screen);
                continue;
            }
            String[] args = arguments(words, line, cl.getSubcommands().keySet());
            if (args == null) {
                continue;
            }
            if (lines.size() > 1) {
                out.println(root.style.label(color ? "› " : "> ") + line); // show which pasted line runs
            }
            root.style = new Style(color, width(terminal)); // the window may have been resized
            running[0] = args;
            cl.execute(args);
            running[0] = null;
            out.println();
            out.flush();
        }
        return true;
    }

    private static int width(Terminal t) {
        int w = t.getWidth();
        return w <= 0 ? 0 : Math.min(w, Style.MAX_WIDTH);
    }

    private static void header(TraceWiseCli root, PrintWriter out) {
        Style st = root.style;
        var facade = root.facade();
        long open = facade.listAlerts(new AlertFilter(null, AlertStatus.NEW, null)).size();
        int waiting = facade.pendingApprovals().size();
        out.println(st.bar("TRACEWISE  AML investigation", root.role.name().toLowerCase(java.util.Locale.ROOT) + " · "
                + root.provider + " · " + root.model));
        out.println(st.dim("  " + open + " new alerts · " + waiting + " proposals waiting for a person"));
        out.println(st.dim("  Type a command (help lists them) or ask a question in plain words. Tab completes,"));
        out.println(st.dim("  Up and Down recall earlier lines, Ctrl+C stops the agent, exit or Ctrl+D quits."));
        out.println();
        out.flush();
    }

    private static void help(CommandLine cl, Style st, PrintWriter out) {
        var rows = new ArrayList<List<String>>();
        cl.getSubcommands().forEach((name, sub) -> {
            String[] description = sub.getCommandSpec().usageMessage().description();
            rows.add(List.of(st.bold(name), description.length == 0 ? "" : description[0]));
        });
        out.println(st.table(List.of("COMMAND", "WHAT IT DOES"), "ll", rows).lines().map(st::cut)
                .reduce((a, b) -> a + "\n" + b).orElse(""));
        out.println(st.dim("  help <command> shows its options, for example: help investigate. Anything else is asked."));
        out.println();
        out.flush();
    }

    /** Ctrl+C: stops an investigation or a triage that is running and says so; anything else finishes on its own. */
    static void stop(TraceWiseCli root, String[] args, PrintWriter out) {
        if (args == null) {
            return; // at the prompt: JLine clears the line
        }
        String alert = alertToStop(args);
        if (alert != null) {
            out.println(root.style.waiting("  Stopping the investigation of " + alert + "..."));
            root.facade().cancelInvestigation(alert);
        } else if (args[0].equals("triage")) {
            out.println(root.style.waiting("  Stopping after this alert..."));
            root.facade().stopTriage();
        } else {
            out.println(root.style.dim("  This command cannot be stopped; it finishes on its own."));
        }
        out.flush();
    }

    /** The alert an investigate line works on: its first argument that is not an option (options may come first). */
    static String alertToStop(String[] args) {
        if (args.length == 0 || !args[0].equals("investigate")) {
            return null;
        }
        return java.util.Arrays.stream(args).skip(1).filter(a -> !a.startsWith("-")).findFirst().orElse(null);
    }
}
