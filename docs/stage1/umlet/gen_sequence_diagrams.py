#!/usr/bin/env python3
"""Generates the TraceWise UMLet sequence diagrams (sd1..sd9 .uxf) using UMLet's
"Sequence all in one" element. Every participant is a class on the UMLet class diagram, and every
message is a method of the receiving class there.

Syntax: ->>> synchronous call, .> return, x->x + self call,
combinedFragment=alt|opt|loop|break~id a b; a:[guard] ... ..=id; a:[guard] ... --=id
"""
import pathlib
from xml.sax.saxutils import escape

HERE = pathlib.Path(__file__).parent
SD = {}

SD["sd1-import-transactions"] = """title=SD1: Import Transactions (UC01; F01)
obj=AML Analyst~a ACTOR
obj=:AlertsView~v
obj=:TraceWiseFacade~f
obj=:ImportService~i
obj=:TransactionRepository~r
obj=:AuditLog~l
a->>>v : onImport(file)
v->>>f : previewImport(file)
f->>>i : preview(file)
i->i + : detectFormat(header)
combinedFragment=break~b1 a i; i:[no format matches]
i.>f : error (expected columns listed)
f.>v : error
v.>a : import refused
--=b1
i.>f : importPreview
f.>v : importPreview
v.>a : show preview
a->>>v : onConfirmImport()
v->>>f : importTransactions(file)
f->>>i : importFile(file)
combinedFragment=loop~l1 i r; i:[for each row]
i->i + : parseRow(row)
i->>>r : exists(id)
r.>i : true or false
combinedFragment=opt~o1 i r; i:[row invalid or duplicate ID]
i->i + : rejectRow(rowNumber, reason)
--=o1
--=l1
i->>>r : saveAll(transactions)
r.>i : saved in one database transaction
i->>>l : append(entry)
i.>f : importSummary
f.>v : importSummary
v.>a : show import summary
"""

SD["sd2-monitoring-and-rules"] = """title=SD2: Run Monitoring and Configure Rules (UC02, UC03; F02)
obj=AML Analyst~a ACTOR
obj=:AlertsView~v
obj=Compliance Supervisor~s ACTOR
obj=:AdminView~ad
obj=:TraceWiseFacade~f
obj=:MonitoringService~m
obj=rule:WindowedRule~w
obj=:Ledger~g
obj=:AlertRepository~ar
obj=:AuditLog~l
a->>>v : onRunMonitoring()
v->>>f : runMonitoring()
f->>>m : run()
combinedFragment=loop~l1 m ar; m:[for each enabled rule]
m->>>w : evaluate(ledger)
w->>>g : accounts()
g.>w : accounts
w->w + : candidates(ledger, account)
w->w + : isMatch(window) for each window()
w.>m : matches
combinedFragment=alt~a1 m ar; m:[kind() is THRESHOLD]
m->m + : recordThreshold(match)
..=a1; m:[kind() is SUSPICION]
m->>>ar : hasOpenAlert(match)
ar.>m : openAlertExists
combinedFragment=opt~o1 m ar; m:[no open alert exists]
m->>>ar : save(alert)
--=o1
--=a1
--=l1
m->>>l : append(entry)
m.>f : monitoringSummary
f.>v : monitoringSummary
v->>>f : listAlerts(filter)
f->>>m : listAlerts(filter)
m->>>ar : find(filter)
ar.>m : alerts
m.>f : alerts
f.>v : alerts
v.>a : show summary and alert queue
s->>>ad : onSaveRule(ruleId, config)
ad->>>f : updateRule(ruleId, config)
f->>>m : updateRule(ruleId, config)
m->>>w : configure(config)
combinedFragment=alt~a2 s l; m:[configuration valid]
w.>m
m->>>l : append(entry with old and new values)
m.>f
f.>ad
ad.>s : rule updated
..=a2; m:[configuration invalid]
w.>m : InvalidConfigurationException
m.>f : InvalidConfigurationException
f.>ad : error
ad.>s : show reason; previous configuration stays
--=a2
"""

SD["sd3-investigate-alert"] = """title=SD3: Investigate Alert (UC04, includes UC05 and UC06; F03, F04, F05)
obj=AML Analyst~a ACTOR
obj=:CaseView~v
obj=:TraceWiseFacade~f
obj=:InvestigationService~i
obj=c:Case~c
obj=trace:AgentTrace~t CREATED_LATER
obj=:AgentRunner~r
obj=:LLMClient~llm
obj=:ToolRegistry~tr
obj=tool:SearchIndicatorsTool~tool
obj=:Retriever~ret
obj=:ClaimVerifier~cv
obj=:AuditLog~l
a->>>v : onInvestigate(alertId)
v->>>f : investigate(alertId, listener)
f->>>i : investigate(alertId, listener)
i->>>c : startInvestigation()
i->>>t : create
i->>>t : addListener(listener)
i->>>t : addListener(auditLog)
i->>>r : run(task, trace)
combinedFragment=loop~l1 v l; r:[until final answer, turn limit (12) or cost limit]
r->>>llm : chat(request)
llm.>r : response
combinedFragment=break~b1 t llm; r:[LLM still failing after 2 retries]
r->>>t : record(step: LLM failure, outcome FAILED)
--=b1
combinedFragment=loop~l2 v l; r:[for each tool call in the response]
r->>>tr : execute(call)
combinedFragment=alt~a1 r ret; r:[arguments valid, tool is search_indicators (UC05)]
tr->>>tool : execute(args)
tool->>>ret : search(query, maxResults)
ret.>tool : passages
tool.>tr : toolResult
tr.>r : toolResult
..=a1; r:[arguments valid, tool is an ActionTool]
ref=tr ret :see SD5 Agent Action and Approval
tr.>r : toolResult (action executed or queued)
..=a1; r:[arguments invalid]
tr.>r : validation error, tool is not run
--=a1
r->>>t : record(step)
t->>>v : onStep(step)
t->>>l : onStep(step)
--=l2
--=l1
r->>>t : finish(outcome)
t->>>v : onFinished(outcome)
t->>>l : onFinished(outcome)
r.>i : outcome
combinedFragment=opt~o2 i cv; i:[outcome COMPLETED or INCOMPLETE]
i->i + : parseFinding(outcome): finding
i->>>cv : verify(finding.claims())
cv.>i : verificationReport (each claim checked against the Ledger)
i->>>c : addFinding(finding)
--=o2
i.>f : investigationResult (no finding if FAILED or CANCELLED)
f.>v : investigationResult
v->v + : showCase(caseId)
a->>>v : onCancel(alertId)
v->>>f : cancelInvestigation(alertId)
f->>>i : cancel(alertId)
i->>>r : cancel()
"""

SD["sd4-triage-queue"] = """title=SD4: Triage Alert Queue (UC07; F07)
obj=AML Analyst~a ACTOR
obj=:AlertsView~v
obj=:TraceWiseFacade~f
obj=:TriageService~tg
obj=:AlertRepository~ar
obj=:InvestigationService~i
a->>>v : onTriage(limits)
v->>>f : triage(limits, listener)
f->>>tg : triage(limits, listener)
tg->>>ar : find(filter: open alerts by risk)
ar.>tg : alerts
combinedFragment=break~b1 a tg; tg:[no open alerts]
tg.>f : triageReport (nothing to triage)
f.>v : triageReport
v.>a : show "Nothing to triage"
--=b1
combinedFragment=loop~l1 a i; tg:[for each alert in risk order]
tg->>>i : investigate(alertId, listener)
ref=tg i :see SD3 Investigate Alert
i.>tg : investigationResult (a FAILED result leaves the alert open)
combinedFragment=opt~o1 a i; a:[Analyst presses Stop]
a->>>v : onStopTriage()
v->>>f : stopTriage()
f->>>tg : stop()
--=o1
combinedFragment=break~b2 a i; tg:[stopRequested, or cost or alert-count limit reached]
tg.>f : partial triageReport
f.>v : triageReport
v.>a : show partial triage summary
--=b2
--=l1
tg.>f : triageReport
f.>v : triageReport
v.>a : show triage summary
"""

SD["sd5-action-and-approval"] = """title=SD5: Agent Action and Approval (UC04 step 5, UC08; F06)
obj=AML Analyst~a ACTOR
obj=:ApprovalsView~av
obj=:TraceWiseFacade~f
obj=:ToolRegistry~tr
obj=:ProposeCloseTool~pt
obj=cmd:CloseCaseCommand~cmd CREATED_LATER
obj=:CommandDispatcher~d
obj=:PermissionPolicy~pp
obj=:ApprovalQueue~q
obj=p:PendingAction~p CREATED_LATER
obj=c:Case~c
obj=:AuditLog~l
tr->>>pt : execute(args)
pt->pt + : createCommand(args)
pt->>>cmd : create
pt->>>d : submit(cmd, agentActor, justification)
d->>>cmd : validate(ctx)
d->>>pp : tierFor(cmd, agentActor)
pp.>d : REQUIRES_APPROVAL
combinedFragment=alt~a1 cmd l; d:[tier is AUTONOMOUS]
d->>>cmd : execute(ctx)
d->>>l : append(entry: executed by agent)
..=a1; d:[tier is REQUIRES_APPROVAL]
d->>>q : enqueue(cmd, agentActor, justification)
q->>>p : create
q->>>cmd : targetCaseId()
q->>>c : submitForApproval()
d->>>l : append(entry: proposed by agent)
--=a1
d.>pt : dispatchResult
pt.>tr : toolResult (proposal queued)
combinedFragment=alt~a3 a l; a:[Analyst approves]
a->>>av : onApprove(pendingId)
av->>>f : approve(pendingId, analystActor)
f->>>q : approve(pendingId, analystActor)
q->>>cmd : validate(ctx)
combinedFragment=alt~a2 cmd l; q:[case state still allows the action]
q->>>cmd : execute(ctx)
cmd->>>c : close()
q->>>p : markApproved(analystActor)
q->>>l : append(entry: approved and executed)
..=a2; q:[case has changed since the proposal]
q->>>p : markExpired()
q->>>l : append(entry: proposal expired)
--=a2
q.>f
f.>av
..=a3; a:[Analyst rejects]
a->>>av : onReject(pendingId, reason)
av->>>f : reject(pendingId, analystActor, reason)
f->>>q : reject(pendingId, analystActor, reason)
q->>>p : markRejected(analystActor, reason)
q->>>c : returnToInvestigation()
q->>>l : append(entry: rejected with reason)
q.>f
f.>av
--=a3
av->av + : refresh()
"""

SD["sd6-ask-assistant-cli"] = """title=SD6: Ask the Assistant through the CLI JSON mode (UC13; F10; Stage 3 KUMA entry point)
obj=Automation Client~ac ACTOR
obj=:AskCli~cli
obj=:TraceWiseFacade~f
obj=:AssistantService~as
obj=:AgentRunner~r
obj=:ClaimVerifier~cv
ac->>>cli : call() from tracewise ask --json
cli->cli + : execute()
cli->>>f : ask(question, context)
f->>>as : ask(question, context)
as->>>r : run(task, trace)
ref=as r :agent loop as in SD3
r.>as : outcome
combinedFragment=alt~a1 f cv; as:[outcome COMPLETED]
as->>>cv : verify(claims)
cv.>as : verificationReport
as.>f : answer with claims, trace and verificationReport
..=a1; as:[outcome FAILED]
as.>f : error after retries
--=a1
f.>cli : answer or error
cli->cli + : print(result)
cli.>ac : JSON on stdout; exit code 0 or non-zero
"""

SD["sd7-report"] = """title=SD7: Draft, Finalise and Export a Suspicious Transaction Report (UC14, UC15; F11)
obj=AML Analyst~a ACTOR
obj=:CaseView~v
obj=:TraceWiseFacade~f
obj=:ReportService~rs
obj=:CaseRepository~cr
obj=:LLMClient~llm
obj=:CommandDispatcher~d
obj=:AuditLog~l
a->>>v : onDraftReport()
v->>>f : draftReport(caseId)
f->>>rs : draft(caseId)
rs->>>cr : findById(caseId)
cr.>rs : case with verified finding
rs->>>llm : chat(request: narrative from the finding)
llm.>rs : narrative
rs.>f : report draft
f.>v : report draft
a->>>v : onRequestFinalisation()
v->>>f : requestFinalisation(caseId)
f->>>rs : requestFinalisation(caseId)
combinedFragment=alt~a1 a d; rs:[a rejected claim remains or a field is empty]
rs.>f : finalisation blocked (reasons)
f.>v : error
v.>a : show what must be fixed
..=a1; rs:[report complete and fully verified]
rs->>>d : submit(escalation command, analystActor, justification)
ref=rs d :see SD5, routed to the ApprovalQueue
d.>rs : queued
rs.>f : pendingAction
f.>v : pendingAction
v.>a : awaiting approval
--=a1
combinedFragment=opt~o1 a l; a:[report finalised after approval (extension point of UC14)]
a->>>v : onExport(formats, dir)
v->>>f : exportReport(caseId, formats, dir)
f->>>rs : export(caseId, formats, dir)
rs->>>l : append(entry: report exported)
rs.>f : paths of the HTML and JSON files
f.>v : paths
v.>a : show file paths
--=o1
"""

SD["sd8-manage-reopen-case"] = """title=SD8: Manage and Reopen a Case (UC09, UC10; F08), showing the State pattern
obj=AML Analyst~a ACTOR
obj=:CaseView~v
obj=:TraceWiseFacade~f
obj=:CaseService~cs
obj=:CommandDispatcher~d
obj=cmd:ActionCommand~cmd
obj=c:Case~c
obj=s:ClosedState~s
obj=:AuditLog~l
a->>>v : showCase(caseId)
v->>>f : getCase(caseId)
f->>>cs : getCase(caseId)
cs.>f : case
f.>v : case
a->>>v : onAddNote(text)
v->>>f : addNote(caseId, text)
f->>>cs : addNote(caseId, text)
cs->>>d : submit(note command, analystActor, "")
d->>>cmd : execute(ctx)
d->>>l : append(entry)
a->>>v : onReopen(reason)
v->>>f : reopenCase(caseId, reason)
f->>>cs : reopen(caseId, reason)
cs->>>d : submit(reopen command, analystActor, reason)
d->>>cmd : execute(ctx)
cmd->>>c : reopen(reason)
c->>>s : reopen(c, reason)
combinedFragment=break~b1 a s; s:[c is not in ClosedState]
s.>c : IllegalTransitionException
c.>cmd : IllegalTransitionException
d.>cs : error
cs.>f : error
f.>v : error
v.>a : only a closed case can be reopened
--=b1
s->>>c : changeState(next: OpenState)
d->>>l : append(entry: reopened with reason)
d.>cs : dispatchResult
cs.>f
f.>v
v.>a : case shown as Open
"""

SD["sd9-read-only-views"] = """title=SD9: Read-Only Views (UC11, UC12, UC16 including UC05, UC17; F08, F09, F04, F12)
obj=AML Analyst~u ACTOR
obj=:AdminView~ad
obj=Compliance Supervisor~s ACTOR
obj=:NetworkView~nv
obj=:TraceWiseFacade~f
obj=:AuditLog~l
obj=:NetworkService~ns
obj=:Retriever~ret
obj=:PerformanceService~ps
obj=:Ledger~g
obj=:CaseRepository~cr
u->>>ad : onSearchAudit(filter)
ad->>>f : queryAudit(filter)
f->>>l : query(filter)
l.>f : entries
f.>ad : entries
ad.>u : show audit entries
u->>>nv : onChangeScope(hops, range)
nv->>>f : buildNetwork(accountId, hops, range, caseId)
f->>>ns : build(accountId, hops, range, caseId)
combinedFragment=loop~l1 ns g; ns:[for each hop (1 to 3)]
ns->>>g : transactionsFor(accountId, range)
g.>ns : transactions
--=l1
ns->>>cr : findById(caseId)
cr.>ns : case (accounts the agent examined)
ns.>f : moneyFlowGraph (at most maxNodes counterparties)
f.>nv : moneyFlowGraph
nv->nv + : render(graph)
u->>>ad : onSearchKnowledge(query)
ad->>>f : searchKnowledge(query)
f->>>ret : search(query, maxResults)
ret.>f : passages
f.>ad : passages
ad.>u : show passages
s->>>ad : onShowMetrics(filter)
ad->>>f : computeMetrics(filter)
f->>>ps : compute(filter)
ps->>>cr : findAll()
cr.>ps : cases
combinedFragment=loop~l2 ps g; ps:[for each labelled transaction]
ps->>>g : findTransaction(id)
g.>ps : transaction
--=l2
ps.>f : performanceMetrics
f.>ad : performanceMetrics
ad.>s : show metrics dashboard
"""

import re

def tidy(text):
    """Puts each guard on the leftmost lifeline of its fragment and adds vertical space after
    fragment headers and around self calls, so labels do not overlap."""
    order = [m.group(1) for m in re.finditer(r"^obj=.*?~(\w+)", text, re.M)]
    first = {}
    out = []
    for line in text.splitlines():
        # ';' separates UMLet commands and ':' starts a label, so neither may appear inside label text
        if line.startswith("ref="):
            head, msg = line.split(" :", 1)
            line = head + " :" + msg.replace(":", ",").replace(";", ",")
        elif " : " in line and not line.startswith("combinedFragment="):
            head, msg = line.split(" : ", 1)
            line = head + " : " + msg.replace(";", ",")
        m = re.match(r"combinedFragment=(\w+)~(\w+) (\w+) (\w+); \w+:(\[.*\])$", line)
        if m:
            kind, fid, x, y, guard = m.groups()
            left = min((x, y), key=order.index)
            first[fid] = left
            out += [f"combinedFragment={kind}~{fid} {x} {y}", f"{left}:{guard}", "tick="]
            continue
        m = re.match(r"\.\.=(\w+); \w+:(\[.*\])$", line)
        if m:
            fid, guard = m.groups()
            out += [f"..={fid}", f"{first[fid]}:{guard}", "tick="]
            continue
        if re.match(r"^(\w+)->\1 \+", line):
            out += ["tick=", line, "tick="]
            continue
        out.append(line)
    return "\n".join(out)

def main(names=None):
    for name, text in SD.items():
        if names and name not in names:
            continue
        text = tidy(text)
        objs = text.count("\nobj=")
        lines = sum(1 for l in text.splitlines() if l and not l.startswith(("title=", "obj=")))
        w, h = max(900, objs * 175), 220 + lines * 38
        xml = ('<?xml version="1.0" encoding="UTF-8" standalone="no"?>\n<diagram program="umlet" version="15.1">'
               f"<zoom_level>10</zoom_level><element><id>UMLSequenceAllInOne</id><coordinates><x>10</x><y>10</y>"
               f"<w>{w}</w><h>{h}</h></coordinates><panel_attributes>{escape(text.strip())}</panel_attributes>"
               "<additional_attributes/></element></diagram>")
        (HERE / f"{name}.uxf").write_text(xml)
        print(name, objs, "participants")

if __name__ == "__main__":
    import sys
    main(sys.argv[1:] or None)
