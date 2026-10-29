package tracewise.ui.cli;

import picocli.CommandLine.Command;

@Command(name = "monitor", description = "Run the monitoring rules.", subcommands = MonitorCli.Run.class)
public class MonitorCli {

    @Command(name = "run", description = "Run every enabled rule over the imported transactions.")
    static class Run extends CliCommandBase {
        @Override
        protected Object execute() {
            return facade().runMonitoring();
        }

        @Override
        protected void print(Object result) {
            super.print(result);
            if (!json) {
                ((tracewise.monitoring.MonitoringSummary) result).failedRules()
                        .forEach(f -> err().println("Rule failed: " + f));
            }
        }
    }
}
