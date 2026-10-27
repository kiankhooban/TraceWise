# AI-Human Collaboration Log

Representative interactions, in the format required by the project instructions (Stage 2, section 10).

---

## Entry 1: Choosing the project (2026-09-29 to 2026-10-03)

**Task:** Choose a project that satisfies the EECS3311 Stage 1 requirements and is relevant to roles at Canadian banks.

**Input/Instruction:** Asked Claude Code to analyse the Stage 1 specification and propose project ideas other than the 20 sample projects, then to research what Canadian Big 5 banks look for in software and data roles.

**AI Contribution:** Summarised the specification requirements. Proposed five ideas built around a deterministic verifier, so the agent's output can be checked automatically. Researched bank job postings, bank AI initiatives (TD's agentic mortgage system, BMO's financial-crimes agent pilot), FINTRAC reporting thresholds, OSFI Guideline E-23, and licensed synthetic AML datasets. Recommended an AML alert-investigation agent.

**Human Contribution:** Rejected generic ideas and redirected the search toward a bank-relevant project. Confirmed with the instructor that the project must be Java only, which ruled out a React frontend. Removed the proposed "adversarial red-team" feature to keep the scope achievable, and moved it to future work pending instructor approval. Rejected the name "Ledgerhound" as hard to read and chose "TraceWise". Questioned the LLM budget, which led to Claude Sonnet 5.5 becoming the default model, with Opus 5.5 as an upgrade.

**Outcome:** The project is TraceWise, a Java AML investigation agent with a JavaFX GUI, a picocli CLI, and LangChain4j.

---

## Entry 2: Stage 3 testing constraints shaping the Stage 1 design (2026-10-03)

**Task:** Make sure the Stage 1 design can support the Stage 3 KUMA agent testing.

**Input/Instruction:** Shared the full project instructions and asked Claude Code to check what the later stages require.

**AI Contribution:** Read the KUMA SDK documentation. KUMA is Python, does not run the agent itself, and captures traces only from code in the same process. Claude Code concluded that the Java agent must expose a machine-readable CLI mode that returns its own tool-call trace. It proposed design additions: `AgentTrace`/`ToolCallRecord` classes, a plain-text agent entry point, and a fault-injecting tool decorator for testing unavailable or malformed tools.

**Human Contribution:** Confirmed that a small Python harness for KUMA is acceptable, since KUMA is mandated by the course. Confirmed that API keys for KUMA are available.

**Outcome:** These additions are included in the Stage 1 class diagram.

---

## Entry 3: UML diagrams and design review (2026-10-03 to 2026-10-04)

**Task:** Produce the Stage 1 UML (use-case, class and sequence diagrams) following the EECS3311 UML
lecture conventions.

**Input/Instruction:** Asked Claude Code to draw every diagram, check each rendered PNG individually,
and follow the lecture's notation rules exactly.

**AI Contribution:** Chose PlantUML over UMLet for automatic layout and single-source consistency,
after rendering notation test sheets. Built a master class model with tag-filtered views, rendered
and inspected every diagram, and caught its own errors during review: attributes duplicating
association lines, a missing multiplicity hidden behind an arrowhead, misleading labels from
orthogonal routing, missing «interface» stereotypes, and several sequence diagrams where an `alt` or
`break` fragment let a failed path continue as if it had succeeded.

**Human Contribution:** Rejected an initial use-case diagram layout and a class-diagram presentation
that was too large to read. Brought in an independent reviewer's critique, which led to a new main
class diagram of major classes, with the detailed views kept as supporting material. Required that
each diagram be checked individually before moving on.

**Outcome:** One use-case diagram, a main class diagram plus eight detailed views, and ten sequence
diagrams, all generated from PlantUML sources in `docs/stage1/diagrams/src/`.

---

## Entry 4: Redrawing the UML in UMLet (2026-10-05 to 2026-10-06)

**Task:** Redraw all Stage 1 UML in UMLet, the tool the course requires, without changing the design.

**Input/Instruction:** Told Claude Code that the diagrams must be made in UMLet, that the design and
scope must not change (only how much of it is drawn), that the PlantUML drafts stay in the repository,
and that every diagram must again be checked individually as a PNG against the Task 2 requirements.

**AI Contribution:** Wrote Python generators that produce UMLet `.uxf` files (a 52-class model drawn
as four class-diagram figures with grey reference boxes, the use-case diagram, and nine sequence
diagrams), exported them with UMLet itself, and inspected each PNG. Added automated checks: no UMLet
parse errors, every sequence-diagram message is a method of the receiving class on the class
diagram, and every message inside a combined fragment stays within the fragment's lifelines. The
visual review and the checks caught errors it then fixed: rule configuration initiated by the wrong
actor, a triage stop drawn after the triage had finished, export drawn as unconditional although it
is an extension, approve and reject drawn as if both happened, a tool call routed through a class
with no association to the retriever, and fragment frames that did not cover the lifelines they
contained. Updated the report's Task 2, 3 and 4 to use only names on the UMLet diagrams.

**Human Contribution:** Brought the UMLet requirement from the course. Rejected an attempt to shrink
the design itself to make the diagrams smaller ("we are just changing the UML, not the actual
structure of the project"), chose the four-figure split, and required the per-diagram checks.

**Outcome:** The report now shows only UMLet diagrams (`docs/stage1/umlet/`), and the PDF is
regenerated with `docs/stage1/build_pdf.py`. The PlantUML drafts remain in `docs/stage1/diagrams/`.

---

## Entry 5: Implementing F01 and F02 test-first (2026-10-20)

**Task:** Build the Stage 1 design for transaction import (F01) and rule-based monitoring (F02),
with the CLI, against the graded design.

**Input/Instruction:** Asked Claude Code (Opus 5.5, with the superpowers TDD skill and the
ponytail skill at level full) to implement week 1 of the Stage 2 plan, committing each working
unit and recording every deviation from the design.

**AI Contribution:** Wrote each test before its code and confirmed it failed first, then wrote
the code, about 67 tests in all. Pinned the exchange rates to Bank of Canada values fetched from
the Bank's Valet API instead of inventing them. Built a seeded scenario generator whose planted
schemes give an exact expected result (4 threshold reports, 4 alerts, no false positives), used
as an integration test. While checking real CLI output it found and fixed its own defects: R4 reported money in plus money out
(\$93,500 for a \$48,000 pass-through), a 7.5-day rule window that would crash a run, a locale
setting that would write decimal commas into the CSV, an alert-to-transaction link bug, and
foreign counterparties receiving threshold reports. Recorded five design changes with reasons.

**Human Contribution:** Chose the tooling after reviewing the options with the AI: Java language
server, ponytail for small code, a session-report plugin for cost reporting, and rejected pstack
because it would re-open the graded design. Required that the Stage 1 design stay the spec and
that changes be justified in the change log.

**Outcome:** F01 and F02 work end to end from the CLI on the committed sample data; design
changes 1 to 5 are in `docs/stage2/design-changes.md`.


---

## Entry 6: The agent core and the first investigation (2026-10-20)

**Task:** Build the agent behind F03 and the claim verifier (F05) from the Stage 1 classes and
sequence diagram SD3, in the front-loaded period 1 plan.

**Input/Instruction:** Asked Claude Code (Opus 5.5) to continue from the handoff document, test
first, one commit per working unit, logging each deviation from the design.

**AI Contribution:** Built the LLM decorators (retry, record, replay) and the factory, the tool
registry that validates every call before it runs, two read tools, the agent loop with its trace,
listeners and limits, the claim verifier, the case lifecycle (State pattern) and the `investigate`
command. Found that LangChain4j already separates retryable from permanent API failures and mapped
them instead of retrying everything. Caught that Jackson reads decimal amounts as `double` and
fixed it before amounts reached the verifier, and made sure the IBM ground-truth laundering label
never reaches the agent. A CLI test records an investigation and replays it through the real
command, so the wiring is tested without spending. Recorded design changes 7 to 13. One
reversal: it first moved the trace out of `AgentFinding`, then restored the original design when
storing findings showed why the design had it there.

**Human Contribution:** Set the priority (finish on time over spreading commits), required the
design to stay the spec with every change justified, and kept the API key out of the AI's reach
(it is read only by the application).

**Outcome:** `tracewise investigate` works on the sample data with Claude Haiku 5.5: all four
planted schemes were escalated and every cited claim was confirmed against the ledger, for CAD 0.012
in total. 134 tests pass.

---

## Entry 7: Multi-agent code review before committing the recordings (2026-10-20)

**Task:** Review the F03 and F05 code before the live runs were committed as demo recordings.

**Input/Instruction:** Kian asked for every change to be reviewed with the project's review skill
before anything else builds on it, and for the live runs to be committed only if the review did
not affect them.

**AI Contribution:** Ran `deep-code-review`: eleven specialist reviewers (correctness, security,
reliability, adversarial, testing and others) plus an independent adversarial pass by a second
model family (OpenAI Codex), then one independent validator per finding. 12 of 15 findings were
confirmed and fixed test-first. The most important: the sample data's ids named the planted
schemes (`STRUCT-0001`, `S-C1`, `N-C29`), so the agent could read the answer from the ids; a
stated total could repeat a transaction and still be confirmed; one tool returned its fields in an
order that changes between runs, which silently broke replay. Three findings were rejected by
their validators (two concurrency cases that cannot happen yet, one deliberate design choice). The
live runs were then recorded again on the corrected data. The first new recording showed the agent
listing a difference as a "total", which the verifier rejected, so the prompt was made explicit.

**Human Contribution:** Required the review before the recordings were committed, which is why the
id leak was found before it reached the demo data.

**Outcome:** 142 tests pass. The four sample alerts replay from `data/replays` without a key, and
every cited claim in them is confirmed against the ledger.

---

## Entry 8: Agent actions with human approval (F06), reviewed twice (2026-10-20)

**Task:** Finish the open items from the first review (cancel tests, retry wait times), build F06,
then review again before recording the demo runs.

**Input/Instruction:** Kian asked to close the two open items first, confirmed F06 belongs to this
week's plan, and asked for the code and other reviews to run again after F06.

**AI Contribution:** Writing the cancel tests exposed a real race: a cancel sent just before a run
started was lost; fixed. Built F06 from the design (Command, Factory Method, State): six actions,
a dispatcher that validates, asks the permission policy and runs or queues each action, an approval
queue with approve, reject and expiry, persisted proposals, action tools for the agent and the
`approvals` CLI. The second `deep-code-review` (eleven reviewers plus OpenAI Codex, each finding
validated) confirmed that the agent cannot approve its own proposals. Three reviewers independently
found that the agent could link more alerts into a case after proposing to close it, so an approval
would close alerts the reviewer never saw. Fixed, together with limiting the agent's actions to the
case it investigates and making the permission policy fail safe (new actions need approval unless
listed). Two concurrency findings were rejected by their validators as not reachable yet; they are
kept for the GUI work. Before re-recording, the AI noticed that showing the agent the global
proposal id would make replays depend on run order, and removed it.

**Human Contribution:** Set the order (open items, then F06, then review) and required reviews
before demo data is committed.

**Outcome:** 167 tests pass. The four sample alerts replay from `data/replays` in any order without
a key: three escalations proposed with every claim confirmed, and one INCONCLUSIVE fan-in where the
agent created a task and proposed nothing.

---

## Entry 9: Closing review items, then queue triage (F07) (2026-10-20)

**Task:** Fix the two items the F06 review left open, then build F07 (triage the alert queue).

**Input/Instruction:** Kian asked for the open items first, then F07.

**AI Contribution:** Proposals made by a run that ends without a finding now expire and free the
case, so nobody can approve an action whose evidence was never produced; a re-investigation now tells
the agent what people decided on its earlier proposals and why. Each change was checked by switching
the behaviour off and watching the new tests fail. Built triage from the design (SD4): open alerts in
risk order, alert and cost limits, stop after the current alert, failed alerts left open and retried
by the next triage, and a summary of proposals, actions, failures and cost. Kept the agent's opening
message unchanged for cases without history, so the committed recordings still replay and a whole
triage of the sample alerts runs offline.

**Human Contribution:** Kept the order: finish open items before new features.

**Outcome:** 178 tests pass. `tracewise triage` works; the period 1 plan is complete. A third
`deep-code-review` (nine reviewers plus Codex, each finding validated) then found that triage could
investigate an alert the agent had just linked into another case (four reviewers agreed), left actions
from failed runs out of the summary (five agreed), and would keep calling an unreachable model for
every alert. All three were fixed test-first; four other findings were rejected by their validators
with reasons recorded. 181 tests pass.

---

## Entry 10: Indicator retrieval and case memory (F04) (2026-10-27)

**Task:** Build F04: retrieval of FINTRAC indicators and similar past cases with a local embedding model.

**Input/Instruction:** Kian asked to start F04 from the handoff, with the usual loop: tests first,
a live check, a full review, and the records kept current.

**AI Contribution:** Copied FINTRAC's 110 general money-laundering indicators verbatim, with source and
date, as the corpus. Before choosing the minimum similarity score, measured the real model on it: a query
in FINTRAC's own words ranked the right indicator first (0.83), the same pattern in raw figures did not
(as the Stage 1 pre-design test had found), and unrelated text stayed below 0.66, so 0.70 was chosen from
data. Built the `Retriever` strategy, `EmbeddingRetriever`, `CaseMemory` fed by approved closes and
escalations, the agent tools `search_indicators` and `similar_cases`, and the CLI commands `indicators
search` and `cases similar`, loading the model only when a command first needs it. In the live run with
Haiku 5.5 the agent searched in FINTRAC's vocabulary and cited the returned indicators word for word on
all four sample alerts. The same run exposed two problems, both fixed test-first: the agent proposed
closing a case it had itself judged INCONCLUSIVE (such proposals now expire), and comparing two cases by
their whole narratives scored any pair about 0.85 (cases are now compared by their indicators, measured
before and after on the live findings).

**Human Contribution:** Set the order and the review standard; owns the decisions recorded in design
changes 21 to 25.

**Outcome:** A fourth `deep-code-review` (ten reviewers plus Codex, one validator per finding) confirmed
eight findings and rejected one. The most important: replays broke after any approval (the adversarial
reviewer reproduced it), indicators the search never returned were accepted, a failed memory load was
never retried, and the memory update ran inside the approval's transaction. All were fixed test-first,
or documented with the measured evidence where the design defers tuning to Stage 3 testing. 223 tests
pass, including a new test that replays the committed recordings.

---

## Entry 11: Checking what was not checked, then the analyst assistant (F10) (2026-10-27)

**Task:** Re-check the items the F04 report left unverified, then build F10 (`tracewise ask`).

**Input/Instruction:** Kian: "first check the things you did not check properly and then start the next task".

**AI Contribution:** Re-ran the full suite with the build result shown (a quiet run had once hidden a compile
error), proved by mutation that the new replay test really guards the recordings, ran the CLI end to end on a
fresh jar, and looked up the new ONNX and tokenizer dependencies on osv.dev (no known vulnerabilities). The x86
check mattered: in an amd64 Linux container every committed replay failed, because the quantized embedding
model scores differ from Apple Silicon in the third decimal and the fifth search result can change. Retrieval
results are now left out of replay matching and the recordings were re-keyed from their stored requests, with
no new model calls; replays now pass on both CPU types and after approvals. Found and closed a gap in the agent
loop on the way: it ran any registered tool the model named, offered or not, which would have let the
read-only assistant reach an action tool. Built F10 from the design: `query_cases`, `AssistantTask` (read-only
tools, clarifying questions, refusals, data never treated as instructions), `AssistantService` with verified
citations and session memory, and `tracewise ask [--json]`.

**Human Contribution:** Asked for the unchecked items to be closed before new work.

**Outcome:** In the live check Haiku 5.5 explained a risky account with every claim confirmed, searched cases
with a filter, asked a clarifying question for "show me the big ones", and declined both a request to close a
case and an instruction to ignore its rules. A fifth `deep-code-review` (ten reviewers plus Codex, one validator
per finding) confirmed seven findings, among them that the design's own "open cases" example returned nothing
(cases are never stored as OPEN), that a clarifying question and an unsupported answer looked the same in JSON,
and that the assistant could not see alerts outside cases. All were fixed test-first and the questions
re-recorded; three findings were rejected with reasons. 254 tests pass.

---

## Entry 12: Front-loaded plan, the JavaFX GUI and case management (F08) (2026-10-27)

**Task:** Build the GUI for every working feature, then F08, and review both.

**Input/Instruction:** Kian asked to do most of the remaining work this week so there is time to recover if
something goes wrong, and agreed to keep one real feature (F12) for period 3 instead of splitting a trivial
change into many commits, which the handout excludes.

**AI Contribution:** Built the JavaFX GUI with the design's five views. Every facade call runs on one background
worker thread, which keeps the window responsive and settles the concurrency items deferred from the CLI work.
The terminal had no screen-recording permission, so the app got a screenshot mode that saves a PNG of every
screen, optionally after a scripted investigation or question; every screen was checked that way, including a
replayed investigation streaming its steps live. Built F08 from SD8: notes, an analyst's close and reopen with
reasons, all through the dispatcher with an audit entry, the case history timeline, audit filters by date and
kind of actor, and CSV export that neutralises spreadsheet formulas.

**Human Contribution:** Set the schedule and the rule against artificial commits.

**Outcome:** A sixth `deep-code-review` (nine reviewers plus Codex, one validator per finding) confirmed twelve
findings, the most serious that Close or Reopen could act on whichever case was on screen when the worker ran
rather than the one clicked. Others: a cancel or stop pressed while the job waited was lost, closing the window
could close the database under a running job, and a linked alert opened the wrong case. All were fixed, the
testable ones test-first; the assistant's questions were re-recorded because `get_case` now shows notes.
283 tests pass.

## Entry 13: Suspicious transaction reports (F11) (2026-10-27)

**Task:** Build F11 (draft, edit, finalise and export a suspicious transaction report) and review it.

**Input/Instruction:** Kian's plan for the week: F11, then F09, each reviewed before the next.

**AI Contribution:** Read the F11 design (feature section, UC14, UC15, SD7) against the running system and found
that the design's "finalise through the escalation" cannot work as written: the agent's escalation is normally
approved before any report exists. Proposed and built finalisation as its own approved command on an escalated
case, which keeps every F11 error case, and logged it (design change 33). The model writes only the narrative,
from confirmed facts; everything else, including the rule that blocks finalisation while a rejected claim is still
in the text, is deterministic. Drafted the three escalated sample cases live with Haiku 5.5 and checked every id,
amount and date in the narratives against the confirmed data, then recorded them so the build replays them.

**Human Contribution:** Set the order of work and the review rule.

**Outcome:** A seventh `deep-code-review` (ten reviewers plus Codex, one validator per finding) confirmed twelve of
sixteen findings. The most important: an edit after a finalisation request left a stale request that blocked a new
one, a redraft from the CLI silently replaced the analyst's edits, and the model was given the agent's unverified
summary as if it were fact (now labelled unverified; the re-recorded narratives no longer repeat its unchecked
remarks). One question is left for Kian: whether the person who requests a finalisation may also approve it.
315 tests pass; the replays pass on Apple Silicon and on x86 Linux.
