package tracewise.ui.cli;

import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import tracewise.retrieval.RetrievedPassage;

@Command(name = "cases", description = "Cases and the memory of past decisions.", subcommands = CasesCli.Similar.class)
public class CasesCli {

    @Command(name = "similar", description = "Closed and escalated cases with a pattern like this case's finding.")
    static class Similar extends CliCommandBase {

        @Parameters(index = "0", description = "The case id, for example CAS-00001.")
        String caseId;

        @Override
        protected Object execute() {
            return facade().similarCases(caseId);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void print(Object result) {
            if (json) {
                super.print(result);
            } else {
                IndicatorsCli.printPassages(out(), (List<RetrievedPassage>) result);
            }
        }
    }
}
