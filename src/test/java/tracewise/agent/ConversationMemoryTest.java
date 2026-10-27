package tracewise.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConversationMemoryTest {

    static AssistantAnswer answer(String text) {
        return new AssistantAnswer(AssistantAnswer.Kind.ANSWER, text, List.of(), List.of(), OutcomeStatus.COMPLETED, null);
    }

    @Test
    void keepsTheLastExchangesOldestFirst() {
        var memory = new ConversationMemory();
        memory.append("one?", answer("1"));
        memory.append("two?", answer("2"));
        memory.append("three?", answer("3"));

        assertEquals(List.of("Q: two?\nA: 2", "Q: three?\nA: 3"), memory.recent(2));
        assertEquals(3, memory.recent(10).size());
        assertEquals(List.of(), new ConversationMemory().recent(5));
    }

    @Test
    void anAnswerWithRejectedClaimsIsRememberedWithThem() {
        var a = answer("C1 received 9,500 CAD.");
        a.attachVerification(new tracewise.verification.VerificationReport(
                tracewise.verification.VerificationStatus.PARTIALLY_VERIFIED,
                List.of(new tracewise.verification.ClaimCheck("Transaction t1", false, "the ledger amount is 9000.00 CAD"))));
        var memory = new ConversationMemory();
        memory.append("q?", a);

        assertEquals(List.of("Q: q?\nA: C1 received 9,500 CAD.\n(Verification rejected: Transaction t1: "
                + "the ledger amount is 9000.00 CAD)"), memory.recent(1));
    }
}
