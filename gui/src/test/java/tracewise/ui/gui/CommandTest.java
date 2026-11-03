package tracewise.ui.gui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The command bar (Ctrl+K): short codes or ids take the analyst to a screen; it never changes data itself. */
class CommandTest {

    @Test
    void codesOpenTheirScreen() {
        assertEquals(new Command(Command.Target.ALERTS, ""), Command.parse("ALRT"));
        assertEquals(new Command(Command.Target.CASES, ""), Command.parse("case"));
        assertEquals(new Command(Command.Target.APPROVALS, ""), Command.parse(" aprv "));
        assertEquals(new Command(Command.Target.ADMIN, ""), Command.parse("ADMN"));
    }

    @Test
    void anIdOpensWhatItNames() {
        assertEquals(new Command(Command.Target.CASES, "CAS-00001"), Command.parse("CASE CAS-00001"));
        assertEquals(new Command(Command.Target.CASES, "CAS-00001"), Command.parse("cas-00001"));
        assertEquals(new Command(Command.Target.ALERTS, "ALR-00004"), Command.parse("ALR-00004"));
        assertEquals(new Command(Command.Target.APPROVALS, "APR-00002"), Command.parse("apr-00002"));
    }

    @Test
    void askSendsTheRestToTheAssistant() {
        assertEquals(new Command(Command.Target.ASK, "Why is account C1037 risky?"),
                Command.parse("ASK Why is account C1037 risky?"));
    }

    @Test
    void anythingElseIsUnknownAndEmptyIsNothing() {
        assertEquals(Command.Target.UNKNOWN, Command.parse("DROP TABLE").target());
        assertNull(Command.parse("   "));
    }
}
