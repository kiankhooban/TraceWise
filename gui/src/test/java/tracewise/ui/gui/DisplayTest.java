package tracewise.ui.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import tracewise.cases.state.CaseStatus;
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
}
