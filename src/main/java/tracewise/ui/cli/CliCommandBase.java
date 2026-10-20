package tracewise.ui.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import tracewise.app.TraceWiseFacade;

/**
 * Template Method: call() fixes the steps every command follows (run, print the result as text
 * or JSON, map failures to exit code 1); each command supplies only execute().
 */
public abstract class CliCommandBase implements Callable<Integer> {

    static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(SerializationFeature.INDENT_OUTPUT);

    @Spec
    CommandSpec spec;

    @Option(names = "--json", description = "Print the result as JSON (for scripts and test harnesses).")
    protected boolean json;

    @Override
    public final Integer call() {
        var root = (TraceWiseCli) spec.root().userObject();
        try {
            print(execute());
            return 0;
        } catch (RuntimeException e) {
            if (json) {
                printJson(Map.of("error", String.valueOf(e.getMessage())));
            } else {
                err().println("Error: " + e.getMessage());
            }
            return 1;
        } finally {
            root.close();
        }
    }

    protected abstract Object execute();

    /** Prints the result; commands override this for a readable text layout and call super for JSON. */
    protected void print(Object result) {
        if (json) {
            printJson(result);
        } else {
            out().println(result);
        }
    }

    protected TraceWiseFacade facade() {
        return ((TraceWiseCli) spec.root().userObject()).facade();
    }

    protected PrintWriter out() {
        return spec.commandLine().getOut();
    }

    protected PrintWriter err() {
        return spec.commandLine().getErr();
    }

    private void printJson(Object value) {
        try {
            out().println(JSON.writeValueAsString(value));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
