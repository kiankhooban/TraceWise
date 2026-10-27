package tracewise.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tracewise.domain.Actor;
import tracewise.domain.ActorType;

class AuditCsvTest {

    @Test
    void writesOneQuotedRowPerEntry() throws Exception {
        var out = new StringWriter();
        AuditCsv.write(List.of(new AuditEntry(Instant.parse("2026-09-01T10:00:00Z"), new Actor("kian", ActorType.ANALYST),
                "ACTION_EXECUTED", "CAS-00001", "Add a note: called \"branch\", line 1\nline 2", "OK")), out);

        assertEquals("timestamp,actor,actor_type,event_type,target,details,outcome\r\n"
                + "2026-09-01T10:00:00Z,kian,ANALYST,ACTION_EXECUTED,CAS-00001,"
                + "\"Add a note: called \"\"branch\"\", line 1\nline 2\",OK\r\n", out.toString());
    }

    @Test
    void textThatASpreadsheetWouldRunAsAFormulaIsNeutralised() throws Exception {
        var out = new StringWriter();
        AuditCsv.write(List.of(new AuditEntry(Instant.parse("2026-09-01T10:00:00Z"), Actor.AGENT, "AGENT_STEP",
                "=HYPERLINK(\"x\")", "+1", "-")), out);

        assertEquals("2026-09-01T10:00:00Z,TraceWise agent,AGENT,AGENT_STEP,\"'=HYPERLINK(\"\"x\"\")\",'+1,'-\r\n",
                out.toString().split("\r\n", 2)[1]);
    }

    @Test
    void aLeadingTabOrCarriageReturnIsNeutralisedToo() throws Exception {
        var out = new StringWriter();
        AuditCsv.write(List.of(new AuditEntry(Instant.parse("2026-09-01T10:00:00Z"), Actor.AGENT, "AGENT_STEP",
                "\t=1+1", "\r=1+1", "@x")), out);

        String row = out.toString().split("\r\n", 2)[1];
        assertEquals(true, row.contains("'\t=1+1") && row.contains("'\r=1+1") && row.contains("'@x"), row);
    }
}
