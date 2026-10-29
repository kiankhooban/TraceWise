package tracewise.ui.cli;

import java.io.PrintWriter;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import tracewise.retrieval.RetrievedPassage;

@Command(name = "indicators", description = "FINTRAC money-laundering indicators (F04).",
        subcommands = IndicatorsCli.Search.class)
public class IndicatorsCli {

    @Command(name = "search", description = "Find the indicators and past cases that match a description.")
    static class Search extends CliCommandBase {

        @Parameters(arity = "1..*", description = "The pattern in plain words, for example: deposits below the "
                + "reporting threshold.")
        List<String> words;

        @Override
        protected Object execute() {
            return facade().searchKnowledge(String.join(" ", words));
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void print(Object result) {
            if (json) {
                super.print(result);
            } else {
                printPassages(out(), (List<RetrievedPassage>) result);
            }
        }
    }

    /** One block per result: score and source, then the text. */
    static void printPassages(PrintWriter out, List<RetrievedPassage> results) {
        if (results.isEmpty()) {
            out.println("Nothing relevant found");
        }
        for (var r : results) {
            out.printf("%.3f  %s%n       %s%n", r.score(), r.source(), r.text());
        }
    }
}
