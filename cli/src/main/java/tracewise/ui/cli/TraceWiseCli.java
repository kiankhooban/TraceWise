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
import tracewise.llm.ModelConfig;

@Command(name = "tracewise", mixinStandardHelpOptions = true, version = "TraceWise 0.1.0",
        description = "AI agent for anti-money-laundering alert investigation.",
        subcommands = {ImportCli.class, MonitorCli.class, AlertsCli.class, ThresholdReportsCli.class,
                RulesCli.class, InvestigateCli.class, ApprovalsCli.class, TriageCli.class,
                IndicatorsCli.class, CasesCli.class, AskCli.class,
                CaseCli.class, AuditCli.class, ReportCli.class, NetworkCli.class, MetricsCli.class})
public class TraceWiseCli {

    @Option(names = "--db", description = "SQLite database file (default: ${DEFAULT-VALUE}).",
            defaultValue = "${env:TRACEWISE_DB:-" + AppConfig.DEFAULT_DB + "}")
    Path database;

    @Option(names = "--role", description = "Who is working: ANALYST or SUPERVISOR (default: ${DEFAULT-VALUE}).",
            defaultValue = "${env:TRACEWISE_ROLE:-" + AppConfig.DEFAULT_ROLE + "}")
    ActorType role;

    @Option(names = "--llm", description = "anthropic; openai for any OpenAI-compatible API (OPENAI_BASE_URL in .env); or replay to answer from "
            + "recorded runs without a key "
            + "(default: ${DEFAULT-VALUE}).", defaultValue = "${env:TRACEWISE_LLM:-" + AppConfig.DEFAULT_PROVIDER + "}")
    String provider;

    @Option(names = "--model", description = "Model id (default: ${DEFAULT-VALUE}).",
            defaultValue = "${env:TRACEWISE_MODEL:-" + AppConfig.DEFAULT_MODEL + "}")
    String model;

    @Option(names = "--record", description = "Save every model answer so the run can be replayed.",
            defaultValue = "${env:TRACEWISE_RECORD:-false}")
    boolean record;

    @Option(names = "--replays", description = "Folder of recorded runs (default: ${DEFAULT-VALUE}).",
            defaultValue = "${env:TRACEWISE_HOME:-.}/data/replays")
    Path replays;

    private TraceWiseFacade facade;

    @picocli.CommandLine.Spec
    picocli.CommandLine.Model.CommandSpec spec;

    TraceWiseFacade facade() {
        if (facade == null) {
            Path home = Path.of(System.getenv().getOrDefault("TRACEWISE_HOME", "."));
            ModelConfig config = ModelConfig.of(provider, model, record);
            if (!config.costKnown()) {
                spec.commandLine().getErr().println("Note: no prices are known for " + model
                        + ", so runs show no cost and cost limits do not apply.");
            }
            facade = TraceWiseBootstrap.create(new AppConfig(database, System.getProperty("user.name"), role,
                    config, replays, home.resolve(".env")));
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
