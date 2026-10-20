package tracewise.ui.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import tracewise.app.AppConfig;
import tracewise.app.TraceWiseBootstrap;
import tracewise.app.TraceWiseFacade;
import tracewise.domain.ActorType;

@Command(name = "tracewise", mixinStandardHelpOptions = true, version = "TraceWise 0.1.0",
        description = "AI agent for anti-money-laundering alert investigation.",
        subcommands = {ImportCli.class, MonitorCli.class, AlertsCli.class, ThresholdReportsCli.class,
                RulesCli.class})
public class TraceWiseCli {

    @Option(names = "--db", description = "SQLite database file (default: ${DEFAULT-VALUE}).",
            defaultValue = "${env:TRACEWISE_DB:-tracewise.db}")
    Path database;

    @Option(names = "--role", description = "Who is working: ANALYST or SUPERVISOR (default: ${DEFAULT-VALUE}).",
            defaultValue = "${env:TRACEWISE_ROLE:-ANALYST}")
    ActorType role;

    private TraceWiseFacade facade;

    TraceWiseFacade facade() {
        if (facade == null) {
            facade = TraceWiseBootstrap.create(new AppConfig(database, System.getProperty("user.name"), role));
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
        var cl = new CommandLine(new TraceWiseCli()).setCaseInsensitiveEnumValuesAllowed(true);
        cl.setOut(out);
        cl.setErr(err);
        return cl;
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new TraceWiseCli()).setCaseInsensitiveEnumValuesAllowed(true).execute(args));
    }
}
