#!/usr/bin/env python3
"""Generates the TraceWise UMLet class diagram (class-diagram.uxf).

The classes, members and relationships below are the "major classes" subset of the full design model
(docs/stage1/diagrams/src/model.iuml): same names and signatures, fewer classes. Graphviz computes the
layout; this script converts it into UMLet elements. Run render.sh afterwards to export the PNG.

UMLet text conventions used: /text/ = italic (abstract), _text_ = underline (static), -- = compartment
separator, <<x>> = stereotype.
"""
import json
import pathlib
import subprocess
from xml.sax.saxutils import escape

HERE = pathlib.Path(__file__).parent
CHAR_W, LINE_H, PAD_W, PAD_H = 7.3, 15, 24, 14

# ---------------------------------------------------------------------------------------------------
# Classes: name -> (package, header lines, attribute lines, method lines)
# Header lines hold stereotypes and the (possibly italic) name. Use "/.../" for italics.
# ---------------------------------------------------------------------------------------------------
C = {}
def cls(pkg, name, header, attrs=(), methods=()):
    C[name] = (pkg, list(header), list(attrs), list(methods))

UI, APP, RULES, AGENT, LLM, ACT, DOM, PER = ("Presentation (GUI and CLI)", "Application", "Monitoring rules",
    "Agent", "LLM access", "Actions and approvals", "Domain and case lifecycle", "Persistence")

cls(UI, "AlertsView", ["<<boundary>>", "AlertsView"], [], ["+onImport(file: Path)", "+onRunMonitoring()", "+onFilter(filter: AlertFilter)", "+onTriage(limits: TriageLimits)"])
cls(UI, "CaseView", ["<<boundary>>", "CaseView"], [], ["+showCase(caseId: String)", "+onInvestigate(alertId: String)", "+onCancel(alertId: String)", "+onReopen(reason: String)", "+onRequestFinalisation()", "+onExport(formats: Set<ExportFormat>, dir: Path)"])
cls(UI, "NetworkView", ["<<boundary>>", "NetworkView"], [], ["+render(graph: MoneyFlowGraph)", "+onChangeScope(hops: int, range: DateRange)"])
cls(UI, "ApprovalsView", ["<<boundary>>", "ApprovalsView"], [], ["+refresh()", "+onApprove(pendingId: String)", "+onReject(pendingId: String, reason: String)"])
cls(UI, "AdminView", ["<<boundary>>", "AdminView"], [], ["+onSaveRule(ruleId: String, config: RuleConfig)", "+onShowMetrics(filter: MetricsFilter)", "+onSearchAudit(filter: AuditFilter)", "+onSearchKnowledge(query: String)"])
cls(UI, "CliCommandBase", ["<<Template Method>>", "/CliCommandBase/"], ["#json: boolean"], ["+call(): Integer", "/#execute(): Object/", "#print(result: Object)"])
cls(UI, "AskCli", ["AskCli"], ["-question: String"], ["#execute(): Object"])

cls(APP, "TraceWiseFacade", ["<<Facade>>", "TraceWiseFacade"], [], [
    "+importTransactions(file: Path): ImportSummary", "+runMonitoring(): MonitoringSummary",
    "+updateRule(ruleId: String, config: RuleConfig)",
    "+investigate(alertId: String, listener: AgentEventListener): InvestigationResult",
    "+triage(limits: TriageLimits, listener: AgentEventListener): TriageReport",
    "+ask(question: String, context: AssistantContext): AssistantAnswer",
    "+approve(pendingId: String, reviewer: Actor)", "+reopenCase(caseId: String, reason: String)",
    "+draftReport(caseId: String): StrReport", "+buildNetwork(accountId: String, hops: int, range: DateRange, caseId: String): MoneyFlowGraph",
    "+computeMetrics(filter: MetricsFilter): PerformanceMetrics", "..."])
cls(APP, "ImportService", ["ImportService"], [], ["+preview(file: Path): ImportPreview", "+importFile(file: Path): ImportSummary"])
cls(APP, "MonitoringService", ["MonitoringService"], [], ["+run(): MonitoringSummary", "+listAlerts(filter: AlertFilter): List<Alert>", "+updateRule(ruleId: String, config: RuleConfig)"])
cls(APP, "InvestigationService", ["InvestigationService"], [], ["+investigate(alertId: String, listener: AgentEventListener): InvestigationResult", "+cancel(alertId: String)"])
cls(APP, "TriageService", ["TriageService"], ["-stopRequested: boolean"], ["+triage(limits: TriageLimits, listener: AgentEventListener): TriageReport", "+stop()"])
cls(APP, "AssistantService", ["AssistantService"], [], ["+ask(question: String, context: AssistantContext): AssistantAnswer"])
cls(APP, "CaseService", ["CaseService"], [], ["+getCase(caseId: String): Case", "+addNote(caseId: String, text: String)", "+reopen(caseId: String, reason: String)"])
cls(APP, "ReportService", ["ReportService"], [], ["+draft(caseId: String): StrReport", "+requestFinalisation(caseId: String): PendingAction", "+export(caseId: String, formats: Set<ExportFormat>, dir: Path): List<Path>"])
cls(APP, "NetworkService", ["NetworkService"], ["-maxNodes: int = 50"], ["+build(accountId: String, hops: int, range: DateRange, caseId: String): MoneyFlowGraph"])
cls(APP, "PerformanceService", ["PerformanceService"], [], ["+compute(filter: MetricsFilter): PerformanceMetrics"])

cls(RULES, "DetectionRule", ["<<interface>>", "<<Strategy>>", "/DetectionRule/"], [], ["/+id(): String/", "/+kind(): RuleKind/", "/+evaluate(ledger: Ledger): List<RuleMatch>/", "/+configure(config: RuleConfig)/"])
cls(RULES, "WindowedRule", ["<<Template Method>>", "/WindowedRule/"], [], ["+evaluate(ledger: Ledger): List<RuleMatch>", "/#candidates(ledger: Ledger, account: Account): List<Transaction>/", "/#isMatch(window: List<Transaction>): boolean/", "/#window(): Duration/"])
cls(RULES, "StructuringRule", ["StructuringRule"], [], ["#candidates(ledger: Ledger, account: Account): List<Transaction>", "#isMatch(window: List<Transaction>): boolean", "#window(): Duration"])

cls(AGENT, "AgentRunner", ["AgentRunner"], [], ["+run(task: AgentTask, trace: AgentTrace): AgentOutcome", "+cancel()"])
cls(AGENT, "AgentTrace", ["<<Observer: subject>>", "AgentTrace"], ["-usage: TokenUsage"], ["+record(step: TraceStep)", "+finish(outcome: AgentOutcome)", "+addListener(listener: AgentEventListener)", "+examinedAccounts(): Set<String>"])
cls(AGENT, "AgentEventListener", ["<<interface>>", "<<Observer>>", "/AgentEventListener/"], [], ["/+onStep(step: TraceStep)/", "/+onFinished(outcome: AgentOutcome)/"])
cls(AGENT, "ToolRegistry", ["ToolRegistry"], [], ["+register(tool: AgentTool)", "+schemas(names: Set<String>): List<ToolSchema>", "+execute(call: ToolCall): ToolResult"])
cls(AGENT, "AgentTool", ["<<interface>>", "/AgentTool/"], [], ["/+name(): String/", "/+schema(): ToolSchema/", "/+execute(args: ToolArguments): ToolResult/"])
cls(AGENT, "ActionTool", ["<<Factory Method>>", "/ActionTool/"], [], ["+execute(args: ToolArguments): ToolResult", "/#createCommand(args: ToolArguments): ActionCommand/"])
cls(AGENT, "ProposeCloseTool", ["ProposeCloseTool"], [], ["#createCommand(args: ToolArguments): ActionCommand"])
cls(AGENT, "ClaimVerifier", ["ClaimVerifier"], ["-toleranceCad: BigDecimal = 0.01"], ["+verify(claims: List<Claim>): VerificationReport"])
cls(AGENT, "Retriever", ["<<interface>>", "/Retriever/"], [], ["/+search(query: String, maxResults: int): List<RetrievedPassage>/"])

cls(LLM, "LLMClient", ["<<interface>>", "/LLMClient/"], [], ["/+chat(request: LLMRequest): LLMResponse/", "/+modelId(): String/"])
cls(LLM, "LangChain4jClient", ["<<Adapter>>", "/LangChain4jClient/"], [], ["+chat(request: LLMRequest): LLMResponse", "+modelId(): String"])
cls(LLM, "ChatModel", ["<<interface>>", "<<LangChain4j library>>", "/ChatModel/"], [], ["/+chat(request: ChatRequest): ChatResponse/"])
cls(LLM, "LLMClientDecorator", ["<<Decorator>>", "/LLMClientDecorator/"], [], ["+chat(request: LLMRequest): LLMResponse", "+modelId(): String"])
cls(LLM, "RetryingLLMClient", ["RetryingLLMClient"], ["-maxRetries: int = 2"], ["+chat(request: LLMRequest): LLMResponse"])

cls(ACT, "ActionCommand", ["<<interface>>", "<<Command>>", "/ActionCommand/"], [], ["/+validate(ctx: CommandContext)/", "/+execute(ctx: CommandContext)/", "/+describe(): String/", "/+targetCaseId(): Optional<String>/"])
cls(ACT, "CloseCaseCommand", ["CloseCaseCommand"], ["-caseId: String"], ["+validate(ctx: CommandContext)", "+execute(ctx: CommandContext)"])
cls(ACT, "CommandDispatcher", ["<<Command: invoker>>", "CommandDispatcher"], [], ["+submit(command: ActionCommand, actor: Actor, justification: String): DispatchResult"])
cls(ACT, "PermissionPolicy", ["PermissionPolicy"], [], ["+tierFor(command: ActionCommand, actor: Actor): PermissionTier"])
cls(ACT, "ApprovalQueue", ["ApprovalQueue"], [], ["+enqueue(command: ActionCommand, actor: Actor, justification: String): PendingAction", "+approve(pendingId: String, reviewer: Actor)", "+reject(pendingId: String, reviewer: Actor, reason: String)", "+pending(): List<PendingAction>"])
cls(ACT, "PendingAction", ["PendingAction"], ["-id: String", "-status: ProposalStatus"], ["+markApproved(reviewer: Actor)", "+markRejected(reviewer: Actor, reason: String)", "+markExpired()"])
cls(ACT, "AuditLog", ["AuditLog"], [], ["+append(entry: AuditEntry)", "+query(filter: AuditFilter): List<AuditEntry>"])

cls(DOM, "Case", ["<<State: context>>", "Case"], ["-id: String"], ["+startInvestigation()", "+submitForApproval()", "+escalate()", "+close()", "+reopen(reason: String)", "+addFinding(finding: AgentFinding)", "~changeState(next: CaseState)"])
cls(DOM, "CaseState", ["<<State>>", "/CaseState/"], [], ["+startInvestigation(c: Case)", "+close(c: Case)", "+reopen(c: Case, reason: String)", "/+status(): CaseStatus/"])
cls(DOM, "ClosedState", ["ClosedState"], [], ["+reopen(c: Case, reason: String)", "+status(): CaseStatus"])
cls(DOM, "Alert", ["Alert"], ["-id: String", "-ruleId: String", "-riskScore: int", "-status: AlertStatus"], [])
cls(DOM, "Transaction", ["Transaction"], ["-id: String", "-timestamp: Instant", "-amountCad: BigDecimal", "-launderingLabel: Boolean"], ["+isInternational(): boolean"])
cls(DOM, "AgentFinding", ["AgentFinding"], ["-verdict: Verdict", "-confidence: double", "-narrative: String"], ["+claims(): List<Claim>", "+attachVerification(report: VerificationReport)"])

cls(PER, "Ledger", ["<<interface>>", "/Ledger/"], [], ["/+accounts(): List<Account>/", "/+transactionsFor(accountId: String, range: DateRange): List<Transaction>/", "/+findTransaction(id: String): Optional<Transaction>/"])
cls(PER, "TransactionRepository", ["<<interface>>", "/TransactionRepository/"], [], ["/+saveAll(transactions: List<Transaction>)/", "/+exists(id: String): boolean/", "/+saveAccount(account: Account)/"])
cls(PER, "CaseRepository", ["<<interface>>", "/CaseRepository/"], [], ["/+save(c: Case)/", "/+findById(id: String): Optional<Case>/", "/+findByAlert(alertId: String): Optional<Case>/", "/+findAll(): List<Case>/"])
cls(PER, "AlertRepository", ["<<interface>>", "/AlertRepository/"], [], ["/+save(alert: Alert)/", "/+find(filter: AlertFilter): List<Alert>/", "/+hasOpenAlert(match: RuleMatch): boolean/", "/+findById(id: String): Optional<Alert>/"])

NOTES = {
    "NoteRules": (RULES, "Also implemented as WindowedRule\nsubclasses: LargeCashRule,\nInternationalTransferRule,\nPassThroughRule, FanInRule,\nFanOutRule", "WindowedRule"),
    "NoteStates": (DOM, "Other concrete states: OpenState,\nUnderInvestigationState,\nPendingApprovalState,\nEscalatedState", "CaseState"),
}

# ---------------------------------------------------------------------------------------------------
# Relationships: (kind, from, to, from_mult, to_mult, label)
# kinds: assoc (from -> to navigable), dep (dashed, label), gen (from = child), real (from = implementer),
#        comp / aggr (from = whole), aggrnav (whole, navigable to part)
# ---------------------------------------------------------------------------------------------------
R = []
def r(kind, a, b, ma="", mb="", label=""):
    R.append((kind, a, b, ma, mb, label))

for v in ["AlertsView", "CaseView", "NetworkView", "ApprovalsView", "AdminView"]:
    r("assoc", v, "TraceWiseFacade", "1", "1")
r("comp", "CaseView", "NetworkView", "1", "1")
r("real", "CaseView", "AgentEventListener"); r("real", "AlertsView", "AgentEventListener")
r("assoc", "CliCommandBase", "TraceWiseFacade", "*", "1", "facade"); r("gen", "AskCli", "CliCommandBase")
for s in ["ImportService", "MonitoringService", "InvestigationService", "TriageService", "AssistantService", "CaseService", "ReportService", "NetworkService", "PerformanceService", "ApprovalQueue", "AuditLog"]:
    r("assoc", "TraceWiseFacade", s, "1", "1")
r("assoc", "TraceWiseFacade", "Retriever", "1", "1", "knowledge")
r("assoc", "TriageService", "InvestigationService", "1", "1"); r("assoc", "TriageService", "AlertRepository", "1", "1")
r("assoc", "ImportService", "TransactionRepository", "1", "1"); r("assoc", "ImportService", "AuditLog", "1", "1")
r("assoc", "MonitoringService", "DetectionRule", "1", "1..*"); r("assoc", "MonitoringService", "AlertRepository", "1", "1")
r("assoc", "MonitoringService", "AuditLog", "1", "1"); r("dep", "MonitoringService", "Alert", label="<<create>>")
r("real", "WindowedRule", "DetectionRule"); r("gen", "StructuringRule", "WindowedRule"); r("dep", "DetectionRule", "Ledger", label="<<use>>")
r("assoc", "InvestigationService", "AgentRunner", "1", "1"); r("assoc", "InvestigationService", "ClaimVerifier", "1", "1")
r("assoc", "InvestigationService", "CaseRepository", "1", "1"); r("assoc", "InvestigationService", "AlertRepository", "1", "1")
r("dep", "InvestigationService", "AgentTrace", label="<<create>>"); r("dep", "InvestigationService", "Case", label="<<create>>")
r("assoc", "AssistantService", "AgentRunner", "1", "1"); r("assoc", "AssistantService", "ClaimVerifier", "1", "1")
r("assoc", "CaseService", "CaseRepository", "1", "1"); r("assoc", "CaseService", "CommandDispatcher", "1", "1")
r("assoc", "ReportService", "CaseRepository", "1", "1"); r("assoc", "ReportService", "LLMClient", "1", "1")
r("assoc", "ReportService", "CommandDispatcher", "1", "1"); r("assoc", "ReportService", "AuditLog", "1", "1")
r("assoc", "NetworkService", "Ledger", "1", "1"); r("assoc", "NetworkService", "CaseRepository", "1", "1")
r("assoc", "PerformanceService", "CaseRepository", "1", "1"); r("assoc", "PerformanceService", "Ledger", "1", "1")
r("assoc", "AgentRunner", "LLMClient", "1", "1"); r("assoc", "AgentRunner", "ToolRegistry", "1", "1"); r("dep", "AgentRunner", "AgentTrace", label="<<use>>")
r("assoc", "AgentTrace", "AgentEventListener", "1", "0..*", "listeners"); r("real", "AuditLog", "AgentEventListener")
r("aggr", "ToolRegistry", "AgentTool", "1", "1..*", "tools")
r("real", "ActionTool", "AgentTool"); r("gen", "ProposeCloseTool", "ActionTool")
r("assoc", "ActionTool", "CommandDispatcher", "*", "1", "dispatcher"); r("dep", "ProposeCloseTool", "CloseCaseCommand", label="<<create>>")
r("assoc", "ClaimVerifier", "Ledger", "1", "1")
r("real", "LangChain4jClient", "LLMClient"); r("assoc", "LangChain4jClient", "ChatModel", "1", "1", "adaptee")
r("real", "LLMClientDecorator", "LLMClient"); r("aggrnav", "LLMClientDecorator", "LLMClient", "0..1", "1", "inner")
r("gen", "RetryingLLMClient", "LLMClientDecorator")
r("real", "CloseCaseCommand", "ActionCommand"); r("dep", "CommandDispatcher", "ActionCommand", label="<<use>>")
r("assoc", "CommandDispatcher", "PermissionPolicy", "1", "1"); r("assoc", "CommandDispatcher", "ApprovalQueue", "1", "1")
r("assoc", "CommandDispatcher", "AuditLog", "1", "1")
r("comp", "ApprovalQueue", "PendingAction", "1", "0..*", "proposals"); r("assoc", "PendingAction", "ActionCommand", "0..1", "1", "command")
r("assoc", "ApprovalQueue", "AuditLog", "1", "1"); r("dep", "ApprovalQueue", "Case", label="<<use>>"); r("dep", "CloseCaseCommand", "Case", label="<<use>>")
r("aggr", "Case", "Alert", "1", "1..*", "alerts"); r("comp", "Case", "AgentFinding", "1", "0..*", "findings")
r("assoc", "Case", "CaseState", "1", "1", "state"); r("gen", "ClosedState", "CaseState")
r("assoc", "Alert", "Transaction", "*", "1..*", "triggeredBy"); r("gen", "TransactionRepository", "Ledger")

# ---------------------------------------------------------------------------------------------------
def lines_of(name):
    _, header, attrs, methods = C[name]
    return header, attrs, methods

def size_of(name):
    header, attrs, methods = lines_of(name)
    texts = header + attrs + methods
    w = max(len(t.strip("/_")) for t in texts) * CHAR_W + PAD_W
    h = (len(header) + max(len(attrs), 1) + max(len(methods), 1)) * LINE_H + PAD_H + 8
    return int(round(max(w, 120) / 10.0) * 10), int(round(h / 10.0) * 10)

def note_size(text):
    ls = text.split("\n")
    return int(max(len(l) for l in ls) * CHAR_W + PAD_W), int(len(ls) * LINE_H + PAD_H)

def layout():
    dot = ["digraph G {", "rankdir=TB; splines=spline; nodesep=0.45; ranksep=0.75; newrank=true;",
           "node [shape=box, fixedsize=true]; edge [arrowhead=none];"]
    pkgs = {}
    for n, (pkg, *_ ) in C.items():
        pkgs.setdefault(pkg, []).append(n)
    for k, (pkg, _, _) in NOTES.items():
        pkgs[pkg].append(k)
    for i, (pkg, members) in enumerate(pkgs.items()):
        dot.append(f'subgraph cluster_{i} {{ label="{pkg}"; margin=28;')
        for m in members:
            w, h = size_of(m) if m in C else note_size(NOTES[m][1])
            dot.append(f'"{m}" [width={w/72:.3f}, height={h/72:.3f}];')
        dot.append("}")
    for kind, a, b, *_ in R:
        # Hierarchy edges are laid out parent above child.
        if kind in ("gen", "real"):
            dot.append(f'"{b}" -> "{a}";')
        else:
            dot.append(f'"{a}" -> "{b}";')
    for k, (_, _, target) in NOTES.items():
        dot.append(f'"{k}" -> "{target}" [style=dashed];')
    dot.append("}")
    out = subprocess.run(["dot", "-Tjson"], input="\n".join(dot), capture_output=True, text=True, check=True)
    return json.loads(out.stdout)

def main():
    g = layout()
    H = float(g["bb"].split(",")[3])
    pos = {}
    for o in g.get("objects", []):
        if "pos" in o and "name" in o and not o["name"].startswith("cluster"):
            x, y = map(float, o["pos"].split(","))
            pos[o["name"]] = (x, H - y)
    els = []
    def box(name):
        w, h = size_of(name) if name in C else note_size(NOTES[name][1])
        cx, cy = pos[name]
        return int(cx - w / 2) + 20, int(cy - h / 2) + 20, w, h
    # packages
    for o in g.get("objects", []):
        if o.get("name", "").startswith("cluster"):
            x1, y1, x2, y2 = map(float, o["bb"].split(","))
            els.append(("UMLPackage", int(x1) + 20, int(H - y2) + 20, int(x2 - x1), int(y2 - y1), o["label"] + "\nbg=#F7F9FC"))
    for name in C:
        x, y, w, h = box(name)
        header, attrs, methods = lines_of(name)
        text = "\n".join(header) + "\n--\n" + ("\n".join(attrs) if attrs else "") + "\n--\n" + "\n".join(methods)
        els.append(("UMLClass", x, y, w, h, text))
    for k, (_, text, _) in NOTES.items():
        x, y, w, h = box(k)
        els.append(("UMLNote", x, y, w, h, text + "\nbg=#FFFBE6"))
    # relations: endpoints on box borders, routed through graphviz edge points
    edges = {}
    for e in g.get("edges", []):
        edges.setdefault((g["objects"][e["tail"]]["name"], g["objects"][e["head"]]["name"]), []).append(e)
    rels = []
    def border_point(name, tx, ty):
        x, y, w, h = box(name)
        cx, cy = x + w / 2, y + h / 2
        dx, dy = tx - cx, ty - cy
        if dx == 0 and dy == 0:
            return cx, cy
        sx = (w / 2) / abs(dx) if dx else 1e9
        sy = (h / 2) / abs(dy) if dy else 1e9
        s = min(sx, sy)
        return cx + dx * s, cy + dy * s
    def route(a, b):
        es = edges.get((a, b)) or edges.get((b, a))
        mids = []
        if es:
            e = es.pop(0)
            pts = [tuple(map(float, p.split(","))) for p in e.get("pos", "").replace("e,", "").replace("s,", "").split() if "," in p]
            pts = [(px + 20, H - py + 20) for px, py in pts]
            if (g["objects"][e["tail"]]["name"], g["objects"][e["head"]]["name"]) != (a, b):
                pts = pts[::-1]
            curve = []
            for i in range(0, len(pts) - 3, 3):
                p0, c1, c2, p3 = pts[i], pts[i + 1], pts[i + 2], pts[i + 3]
                for t in (0.25, 0.5, 0.75, 1.0):
                    u = 1 - t
                    curve.append((u**3 * p0[0] + 3 * u * u * t * c1[0] + 3 * u * t * t * c2[0] + t**3 * p3[0],
                                  u**3 * p0[1] + 3 * u * u * t * c1[1] + 3 * u * t * t * c2[1] + t**3 * p3[1]))
            mids = curve[:-1]
        ax, ay = box(a)[0] + box(a)[2] / 2, box(a)[1] + box(a)[3] / 2
        bx, by = box(b)[0] + box(b)[2] / 2, box(b)[1] + box(b)[3] / 2
        first = mids[0] if mids else (bx, by)
        last = mids[-1] if mids else (ax, ay)
        return [border_point(a, *first)] + mids + [border_point(b, *last)]
    for kind, a, b, ma, mb, label in R:
        if kind in ("gen", "real"):
            pts = route(b, a)  # start at the parent, where the triangle is drawn
            lt = "<<-" if kind == "gen" else "<<."
            attrs = [f"lt={lt}"]
        elif kind in ("comp", "aggr", "aggrnav"):
            pts = route(a, b)  # start at the whole, where the diamond is drawn
            lt = {"comp": "<<<<<-", "aggr": "<<<<-", "aggrnav": "<<<<->"}[kind]
            attrs = [f"lt={lt}", f"m1={ma}", f"m2={mb}"] + ([label] if label else [])
        elif kind == "assoc":
            pts = route(a, b)
            attrs = ["lt=->", f"m1={ma}", f"m2={mb}"] + ([label] if label else [])
        else:  # dep
            pts = route(a, b)
            attrs = ["lt=.>"] + ([label] if label else [])
        rels.append((pts, "\n".join(attrs)))
    for k, (_, _, target) in NOTES.items():
        rels.append((route(k, target), "lt=."))
    xml = ['<?xml version="1.0" encoding="UTF-8" standalone="no"?>', '<diagram program="umlet" version="15.1">', "<zoom_level>10</zoom_level>"]
    xml.append(f'<element><id>Text</id><coordinates><x>20</x><y>0</y><w>1200</w><h>30</h></coordinates><panel_attributes>{escape("*TraceWise: Class Diagram (major classes and interfaces)*")}\nfontsize=18</panel_attributes><additional_attributes/></element>')
    for kind, x, y, w, h, text in els:
        xml.append(f"<element><id>{kind}</id><coordinates><x>{x}</x><y>{y + 20}</y><w>{w}</w><h>{h}</h></coordinates>"
                   f"<panel_attributes>{escape(text)}</panel_attributes><additional_attributes/></element>")
    for pts, attrs in rels:
        xs = [p[0] for p in pts]; ys = [p[1] + 20 for p in pts]
        ox, oy = int(min(xs)) - 10, int(min(ys)) - 10
        w, h = int(max(xs) - ox) + 20, int(max(ys) - oy) + 20
        rel_pts = ";".join(f"{px - ox:.1f};{py - oy:.1f}" for px, py in zip(xs, ys))
        xml.append(f"<element><id>Relation</id><coordinates><x>{ox}</x><y>{oy}</y><w>{w}</w><h>{h}</h></coordinates>"
                   f"<panel_attributes>{escape(attrs)}</panel_attributes><additional_attributes>{rel_pts}</additional_attributes></element>")
    xml.append("</diagram>")
    (HERE / "class-diagram.uxf").write_text("\n".join(xml))
    print(f"classes: {len(C)}, relationships: {len(R)}")

if __name__ == "__main__":
    main()
