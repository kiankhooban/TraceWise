package tracewise.ui.cli;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import tracewise.agent.AgentTrace;
import tracewise.agent.AssistantAnswer;
import tracewise.agent.AssistantContext;

@Command(name = "ask", description = "Ask the assistant about transactions, accounts, alerts and cases (read-only). "
        + "With --json: the answer, every tool call and the verification, for scripts and test harnesses.")
public class AskCli extends CliCommandBase {

    @Parameters(arity = "1..*", description = "The question, for example: Why is account C1037 risky?")
    List<String> words;

    @Option(names = "--case", description = "The case you are looking at.")
    String caseId;

    @Option(names = "--account", description = "The account you are looking at.")
    String accountId;

    @Override
    protected Object execute() {
        return facade().ask(String.join(" ", words), new AssistantContext(caseId, accountId));
    }

    @Override
    protected void print(Object result) {
        var a = (AssistantAnswer) result;
        if (json) {
            super.print(toJson(a));
            return;
        }
        Style st = style();
        out().println();
        out().println(st.wrap(a.text(), 2));
        out().println();
        if (a.kind() == AssistantAnswer.Kind.CLARIFY) {
            out().println(st.field("Reply", st.waiting("needs more detail") + ": ask again with it"));
        } else if (a.kind() == AssistantAnswer.Kind.DECLINE) {
            out().println(st.field("Reply", st.waiting("declined") + ": the assistant only reads; decisions go through approvals"));
        }
        if (!a.claims().isEmpty()) {
            out().println(st.field("Cited", String.join("  ", a.claims().stream().map(c -> c.transactionId()).distinct().toList())));
        }
        out().println(st.field("Checked", st.check(a.verification()) + "  " + a.verification().summary()));
        a.verification().rejected().forEach(c -> out().println(st.wrap(st.bad("REJ") + " " + c.claim() + ": " + c.reason(), 12)));
        out().println(st.dim("  " + InvestigateCli.cost(a.trace())));
    }

    private static Map<String, Object> toJson(AssistantAnswer a) {
        var out = new LinkedHashMap<String, Object>();
        out.put("status", a.status());
        out.put("kind", a.kind());
        out.put("answer", a.text());
        out.put("claims", a.claims());
        out.put("totals", a.totals());
        out.put("verification", Map.of("status", a.verification().status(), "summary", a.verification().summary(),
                "checks", a.verification().checks()));
        AgentTrace t = a.trace();
        var trace = new LinkedHashMap<String, Object>();
        trace.put("model", t.pricing().modelName());
        trace.put("usage", t.usage());
        trace.put("costCad", t.costCad());
        trace.put("steps", t.steps());
        out.put("trace", trace);
        return out;
    }
}
