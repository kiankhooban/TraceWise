package tracewise.ui.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Actor;
import tracewise.domain.ActorType;
import tracewise.domain.AlertStatus;

/** How raw values read on screen (docs/stage2/ui-design.md): every state is a word, never a code or a colour alone. */
class DisplayTest {

    @Test
    void riskScoresReadAsALevelWithTheScore() {
        assertEquals("HIGH", Display.riskLevel(60));
        assertEquals("MED", Display.riskLevel(59));
        assertEquals("MED", Display.riskLevel(45));
        assertEquals("LOW", Display.riskLevel(44));
        assertEquals("risk-high", Display.riskStyle(65));
        assertEquals("risk-low", Display.riskStyle(0));
    }

    @Test
    void rulesReadWithTheirStage1Names() {
        assertEquals("Structuring", Display.ruleName("R3"));
        assertEquals("Rapid pass-through", Display.ruleName("R4"));
        assertEquals("Large international transfer", Display.ruleName("R2"));
        assertEquals("", Display.ruleName("R9"));
    }

    @Test
    void statusesReadAsWordsNotEnumNames() {
        assertEquals("In case", Display.label(AlertStatus.IN_CASE));
        assertEquals("Pending approval", Display.label(CaseStatus.PENDING_APPROVAL));
        assertEquals("Under investigation", Display.label(CaseStatus.UNDER_INVESTIGATION));
        assertEquals("", Display.label(null));
    }

    @Test
    void agesAreShortAndSortableByEye() {
        assertEquals("now", Display.age(Duration.ofSeconds(40)));
        assertEquals("14m", Display.age(Duration.ofMinutes(14)));
        assertEquals("2h 05m", Display.age(Duration.ofMinutes(125)));
        assertEquals("3d 04h", Display.age(Duration.ofHours(76)));
        assertEquals("now", Display.age(Duration.ofMinutes(-5)), "a clock ahead of the data is not a negative age");
    }

    @Test
    void actorsReadAsAgentHumanOrSystem() {
        assertEquals("AGENT", Display.actor(Actor.AGENT));
        assertEquals("SYSTEM", Display.actor(Actor.SYSTEM));
        assertEquals("HUMAN", Display.actor(new Actor("kian", ActorType.ANALYST)));
        assertEquals("HUMAN", Display.actor(new Actor("lead", ActorType.SUPERVISOR)));
        assertEquals("", Display.actor(null));
    }

    @Test
    void aFilterLabelReadsBackAsItsStatus() {
        assertEquals(AlertStatus.IN_CASE, Display.parse(AlertStatus.class, "In case"));
        assertEquals(CaseStatus.PENDING_APPROVAL, Display.parse(CaseStatus.class, "Pending approval"));
        assertNull(Display.parse(AlertStatus.class, "All"), "a label that is no status means no filter");
    }

    @Test
    void aFindingReadsAsItsVerdictAndItsCheck() {
        var f = new tracewise.agent.AgentFinding(tracewise.agent.Verdict.ESCALATE, 0.7, java.util.List.of(), java.util.List.of(),
                java.util.List.of(), "n", false, null);
        assertEquals("verdict-ESCALATE", Display.verdictStyle(f));
        assertNull(Display.verdictStyle(null));
        var ok = new tracewise.verification.VerificationReport(tracewise.verification.VerificationStatus.VERIFIED, java.util.List.of());
        var partial = new tracewise.verification.VerificationReport(tracewise.verification.VerificationStatus.PARTIALLY_VERIFIED,
                java.util.List.of());
        assertEquals("VER", Display.checkCode(ok));
        assertEquals("ok", Display.checkStyle(ok));
        assertEquals("UNVER", Display.checkCode(partial));
        assertEquals("bad", Display.checkStyle(partial));
    }

    @Test
    void numberColumnsSortByValueNotByText() {
        var sorted = new java.util.ArrayList<>(java.util.List.of("100", "9", "48,000.00", "HIGH 65", "", "LOW 43"));
        sorted.sort(Display.BY_NUMBER);
        assertEquals(java.util.List.of("", "9", "LOW 43", "HIGH 65", "100", "48,000.00"), sorted, "blank first, then by value");
        assertEquals(new java.math.BigDecimal("48000.00"), Display.number("48,000.00"));
        assertNull(Display.number("2h 05m"), "an age is not a number");
    }
}
