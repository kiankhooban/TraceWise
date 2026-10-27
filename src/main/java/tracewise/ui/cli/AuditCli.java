package tracewise.ui.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import tracewise.audit.AuditFilter;
import tracewise.domain.ActorType;

@Command(name = "audit", description = "The append-only audit trail (F08).", subcommands = AuditCli.Export.class)
public class AuditCli {

    @Command(name = "export", description = "Write audit entries as CSV, to a file or the terminal.")
    static class Export extends CliCommandBase {

        @Option(names = "--from", description = "First day included, YYYY-MM-DD (UTC).")
        LocalDate from;

        @Option(names = "--to", description = "Last day included, YYYY-MM-DD (UTC).")
        LocalDate to;

        @Option(names = "--event", description = "Event type, for example IMPORT or AGENT_STEP.")
        String event;

        @Option(names = "--target", description = "A case, alert or account id.")
        String target;

        @Option(names = "--actor", description = "ANALYST, SUPERVISOR, AGENT or SYSTEM.")
        ActorType actor;

        @Option(names = "--out", description = "CSV file to write (default: print the CSV).")
        Path file;

        @Override
        protected Object execute() {
            var filter = new AuditFilter(event, target, from == null ? null : from.atStartOfDay().toInstant(ZoneOffset.UTC),
                    to == null ? null : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC), actor);
            if (file == null) {
                facade().exportAudit(filter, out());
                return null;
            }
            try (var w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                return facade().exportAudit(filter, w) + " entries written to " + file;
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot write " + file + ": " + e.getMessage(), e);
            }
        }

        @Override
        protected void print(Object result) {
            if (result != null) {
                super.print(result);
            }
        }
    }
}
