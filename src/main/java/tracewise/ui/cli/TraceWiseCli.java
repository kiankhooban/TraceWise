package tracewise.ui.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import tracewise.app.AppConfig;
import tracewise.app.TraceWiseBootstrap;
import tracewise.app.TraceWiseFacade;

@Command(name = "tracewise", mixinStandardHelpOptions = true, version = "TraceWise 0.1.0",
        description = "AI agent for anti-money-laundering alert investigation.",
        subcommands = {ImportCli.class})
public class TraceWiseCli {

    @Option(names = "--db", description = "SQLite database file (default: ${DEFAULT-VALUE}).",
            defaultValue = "${env:TRACEWISE_DB:-tracewise.db}")
    Path database;

    private TraceWiseFacade facade;

    TraceWiseFacade facade() {
        if (facade == null) {
            facade = TraceWiseBootstrap.create(new AppConfig(database, System.getProperty("user.name")));
        }
        return facade;
    }

    void close() {
        if (facade != null) {
            facade.close();
            facade = null;
        }
    }

    static CommandLine commandLine(PrintWriter out, PrintWriter err) {
        var cl = new CommandLine(new TraceWiseCli());
        cl.setOut(out);
        cl.setErr(err);
        return cl;
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new TraceWiseCli()).execute(args));
    }
}
