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
                printPassages(out(), style(), (List<RetrievedPassage>) result);
            }
        }
    }

    /** "  0.831  FINTRAC 7.5, ..." on one line, the source cut to fit the width. */
    static String sourceLine(Style st, String score, String source) {
        String line = st.cut("  " + score + "  " + source);
        int start = score.length() + 4;
        return "  " + st.dim(score) + "  " + st.label(line.length() > start ? line.substring(start) : "");
    }

    /** One block per result: score and source, then the text under it. */
    static void printPassages(PrintWriter out, Style st, List<RetrievedPassage> results) {
        if (results.isEmpty()) {
            out.println("  Nothing relevant found");
        }
        for (var r : results) {
            out.println(sourceLine(st, String.format("%.3f", r.score()), r.source()));
            out.println(st.wrap(r.text(), 9));
        }
        if (!results.isEmpty()) {
            out.println(st.dim("  Score: how close each match is to the words searched, 1 is the closest"));
        }
    }
}
