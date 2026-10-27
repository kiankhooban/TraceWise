# Stage 2 handoff (read this first after /compact or in a new session)

Last updated 2026-10-27, during the GUI and F08 work (commit `9ab8e5d`). Read this file, then `CLAUDE.md` (repo root) and
`docs/stage2/PLAN.md`. Sessions often run from `~/Brain`, where the TraceWise `CLAUDE.md` is NOT
loaded automatically: read it explicitly.

## 1. Where we are

- Stage 1 graded 100/100 (tag `stage1-submitted`). Stage 2 due **2026-11-10**; feature freeze Nov 7;
  Kian records the demo videos Nov 8 to 10 (we write a script per feature in week 3).
- Period 1 (Oct 20 to 26) plan is **complete** on Oct 20: 53+ commits pushed (5 needed per period).
  Periods 2 (Oct 27 to Nov 2) and 3 (Nov 3 to 9) still each need 5+ meaningful code commits.
- 272 tests pass, 1 skipped (the opt-in live API test). Design changes 1 to 29 logged (GUI and F08 changes still to log).
- Period 2 (Oct 27 to Nov 2): 12 code commits plus 2 records commits on Oct 27 (F04, the replay fix, F10 and their review fixes). No PRs: pushed to main.

| Feature | State | Main code |
|---|---|---|
| F01 Import | CLI done | `ingest/*`, `app/ImportService`, `ui/cli/ImportCli` |
| F02 Monitoring, threshold register, alert queue | CLI done | `monitoring/*`, `app/MonitoringService`, `ui/cli/{Monitor,Alerts,ThresholdReports,Rules}Cli` |
| F03 Investigation | CLI done | `agent/*` (`AgentRunner`, `AgentTrace`, `InvestigationTask`), `agent/tools/*`, `llm/*`, `app/InvestigationService`, `ui/cli/InvestigateCli` |
| F05 Claim verification | done (inside investigate) | `verification/*`, `agent/Claim`, `agent/TotalClaim` |
| F06 Actions and approval queue | CLI done | `actions/*`, `agent/tools/ActionTool` and 6 action tools, `agent/tools/ActionScope`, `ui/cli/ApprovalsCli` |
| F07 Triage | CLI done | `app/TriageService`, `app/TriageLimits`, `agent/TriageReport`, `ui/cli/TriageCli` |
| F08 Case management, audit | core done: notes, analyst close, reopen, history, audit filters and CSV; CLI and GUI pieces in progress | `actions/{AddNote,ReopenCase}Command`, `app/CaseService`, `audit/{AuditFilter,AuditCsv}`, `domain/CaseNote` |
| F04 Retrieval and case memory | CLI and agent tools done | `retrieval/*`, `agent/tools/{SearchIndicators,SimilarCases}Tool`, `ui/cli/{Indicators,Cases}Cli`, `resources/tracewise/fintrac-indicators.tsv` |
| F10 Assistant | CLI done (`ask --json`) | `agent/Assistant*`, `agent/ConversationMemory`, `agent/tools/{QueryCases,QueryAlerts,GetCase}Tool`, `app/AssistantService`, `ui/cli/AskCli` |
| F09, F11, F12 | not started | |
| GUI (JavaFX 21) | Alerts (import, monitoring, queue, register, investigate, triage), Cases (finding, live steps, evidence, tasks), Approvals, Admin (rules, knowledge, audit log), Assistant panel | `ui/gui/*`; launch `./tracewise-gui` |

All nine designed patterns are in code: Facade (`TraceWiseFacade`), Strategy (`DetectionRule`,
`TransactionFormat`), Template Method (`WindowedRule`, `CliCommandBase`), Adapter (`LangChain4jClient`),
Decorator (`LLMClientDecorator`: retry, record), Factory Method (`LLMClientFactory`,
`ActionTool.createCommand`), Observer (`AgentTrace` and `AgentEventListener`, `AuditLog` listens),
State (`CaseState` and 5 states), Command (`ActionCommand`, `CommandDispatcher`, `ApprovalQueue`).
Strategy also covers `Retriever` (`EmbeddingRetriever`, context `SearchIndicatorsTool`).

## 2. Next tasks, in order (plan agreed with Kian 2026-10-27, see PLAN.md "Revised schedule")

1. **F08 finish**: CLI `case show|note|reopen|close` and `audit export --from --to [--actor] [--out]`; GUI case
   view: Notes and History tabs, Reopen and Close buttons; Admin audit log: date and actor filters, Export CSV.
   Then log design changes (GUI threading, launcher, screenshot mode, CaseService through the dispatcher,
   AuditFilter fields, human close but no direct human escalate), README GUI section, review (deep-code-review).
2. **F11** STR draft and export (Report tab), **F09** network view (Network tab, JavaFXSmartGraph). This week.
3. Period 3 (Nov 3 to 6): **F12** dashboard (the real period 3 feature), final review fixes, fresh-clone check.
4. Records: UMLet class diagram update, stage2-report PDF, demo scripts.

Design sources: `docs/stage1/diagrams/src/model.iuml` (full model with every member; grep the class),
`docs/stage1/Stage1-Design-Report.md` (feature sections F01 to F12, use cases UC01 to UC17, Task 3
traceability, Task 4 patterns), sequence diagrams SD1 to SD6 in `docs/stage1/umlet/gen_sequence_diagrams.py`.

## 3. How Kian wants the work done (standing instructions)

- **Order**: finish open items before starting the next feature.
- **Every feature gets reviewed** with `deep-code-review` before it is called done. Confirmed findings
  are fixed test-first, then demo recordings are re-made if the agent's requests changed. Reviews only
  via `/code-review` or `deep-code-review` (no other review paths).
- **Skills**: superpowers TDD (watch each test fail first; when a compile error hides the first run,
  switch the behaviour off and confirm the test fails), ponytail level full (never ultra; the Stage 1
  design counts as requested, so its patterns are never cut), context7 for library APIs.
- The Stage 1 design is the spec. Every deviation goes in `docs/stage2/design-changes.md` immediately.
- Commit after each working unit and push; end messages with the session's `Claude-Session:` trailer.
- No em dashes anywhere. Discuss before large or outward-facing actions. Kian owns the code.
- Keep records current: `docs/ai-usage/collaboration-log.md` (one entry per milestone; Entries 1 to 9
  so far), `docs/ai-usage/ai-tools.md` (tools and costs), `docs/stage2/PLAN.md` (status, progress log).

## 4. The working loop per feature

```text
read design -> tests first (RED) -> code (GREEN) -> commit and push per unit -> live check with Haiku
-> deep-code-review (reviewers plus Codex) -> one validator per finding -> fix confirmed ones test-first
-> re-record data/replays if prompt or tools changed -> verify replay -> update records -> commit and push
```

How the reviews were run (cheap and repeatable):
- Stage the diff in a run dir `/tmp/compound-engineering/ce-code-review/<run-id>/` (`full.diff`, `files.txt`).
- Build each reviewer's prompt file with a short Python script: take lines 10 to 184 of
  `~/.claude/skills/deep-code-review/references/subagent-template.md`, fill `{persona_file}`
  (from `references/personas/<name>.md`), `{diff_scope_rules}`, `{schema}`, `{run_id}`, `{reviewer_name}`,
  `{intent_summary}`, `{file_list}` and `{diff}` (as paths), then dispatch each agent with "Read <prompt
  file> and follow it exactly". Correctness, security and adversarial run on the session model; the rest
  on Sonnet. Agent-native and learnings get their persona file plus a review-context block.
- Codex pass: `bash ~/.claude/skills/deep-code-review/scripts/cross-model-adversarial-review.sh codex <base> <run-dir>`,
  in the background, from `~/TraceWise`; it writes `adversarial-codex.json`.
- Validators: one per finding from `references/validator-template.md` (Sonnet). Rejected findings are
  dropped; the ones that are deferred rather than wrong go in section 6 below.
- Five reviews so far: F03/F05, F06, F07 (2026-10-20), F04 and F10 (2026-10-27). See collaboration log Entries 7 to 11.
- Pitfall: quiet test runs (`./mvnw -q test | grep ...`) hide compile errors; always also check `BUILD SUCCESS`.

## 5. Conventions and pitfalls

Code:
- Packages: `domain, ingest, monitoring, persistence, audit, app, llm, agent, agent.tools, actions,
  cases.state, verification, retrieval, ui.cli, tools`.
- New user operations: service in `app`, method on `TraceWiseFacade`, wiring in `TraceWiseBootstrap.create`.
  `TraceWiseBootstrap.create(config, llmSupplier)` lets tests record through the real wiring;
  `investigationTools(...)` is the single place the agent's tools are registered.
- CLI commands extend `CliCommandBase` (`execute()`, optional `print()`; `--json` is automatic) and are
  listed in the `TraceWiseCli` subcommands.
- Persistence: repositories take `Database` and use `Jdbc.query/update`; schema in
  `src/main/resources/tracewise/schema.sql` (CREATE IF NOT EXISTS, no migrations; split on `;` plus
  newline before CREATE). `db.inTransaction(Runnable)` and `inTransaction(Supplier)` nest. A lambda that
  returns a value picks the Supplier overload (that once caused infinite recursion).
- Money from JSON: always `ExactJson.mapper()` (exact BigDecimal, scale kept).
- Agent safety: `PermissionPolicy` lists what the agent may do alone (priority, link, watchlist, task);
  everything else needs a human. `ActionScope` limits agent actions to the case under investigation.
  Only `Actor.isHuman()` can approve or reject. Linking into a case waiting for approval is refused.
  A run that ends without a finding expires its proposals; re-runs see earlier decisions.
- Tools expose `NAME` constants; use them instead of string literals.
- GUI: every facade call goes through `GuiWorker` (one background thread, in order; results via
  Platform.runLater), so the single DB connection is never shared; cancel and stop only set flags and are called
  directly. Views get a `Gui` context (facade, worker, stage, dialogs). Verify screens without screen access:
  `TRACEWISE_SNAPSHOT=<dir> [TRACEWISE_SNAPSHOT_INVESTIGATE=ALR-00001] [TRACEWISE_SNAPSHOT_ASK="..."] ./tracewise-gui`
  saves a PNG of every tab and sub-tab, then exits (Read the PNGs). The terminal has no screen-recording permission.
- Retrieval: the bge-small model loads lazily (about 2 s) through `TraceWiseBootstrap.indicatorRetriever` and
  `CaseMemory`; unit tests use `retrieval/WordCountEmbeddingModel`, never the ONNX model (only
  `FintracRetrievalTest`, `KnowledgeCliTest` and `CommittedReplaysTest` load the real one). Minimum score 0.70,
  measured (design change 21). `CaseMemory.remember()` only marks the memory stale; it rebuilds from storage.
- A run's waiting close or escalation that its own verdict does not support expires (`Verdict.supportsClose()`,
  `supportsEscalation()`); claim verification also checks the finding's indicators against what
  `search_indicators` returned.
- Parties outside the bank: account `CASH` or prefix `EXT-`. Session role: `--role` or `TRACEWISE_ROLE`.

Tests:
- `@TempDir` real SQLite. Shared doubles: `monitoring/InMemoryLedger`, `agent/AgentRunnerTest.ScriptedModel`
  (`then(response or exception)`, `beforeEachCall` hook, `requests`), `actions/ActionsFixture`.
- Sample data: `ScenarioGenerator.generate(42)`; ids are neutral (`C1037`, `EXT-2101`, `TX-00001`).
  `ScenarioGenerator.accounts(42)` maps role names (S-C1 structuring, P-C1 pass-through, F-C1 fan-in,
  O-C1 fan-out, L-C1/L-C2 large cash, I-C1/I-C2 international, N-Cxx normal) to ids. Expected monitoring:
  4 threshold reports; alerts ALR-00001 R3, ALR-00002 R4, ALR-00003 R5, ALR-00004 R6. Never put scheme
  names in anything the agent can read.

Replays and live runs:
- `data/replays` holds Claude Haiku 5.5 recordings of the four sample alerts (with actions and F04 retrieval)
  and five assistant questions asked after the replayed triage (list in `CommittedReplaysTest`). The results of
  `search_indicators` and `similar_cases` are left out of replay matching (`TraceWiseBootstrap.replayStore`):
  the quantized embedding model scores differently on ARM and x86. `CommittedReplaysTest` replays everything on
  the shipped sample data and passes on Apple Silicon and on x86 (checked in Docker:
  `--platform linux/amd64`, image `maven:3.9-eclipse-temurin-21`; the Maven wrapper's tar fails under that
  emulation, and copy the tree from `git ls-files` so `.env` never goes in). A rejection still changes the
  next run's prompt, so re-investigations after a rejection need a live model.
  on a fresh database in any order: `./tracewise --llm replay --model claude-haiku-5-5 investigate ALR-00001`
  or `... triage`. Replay matches whole requests, so after ANY change to the prompt, tool set, tool
  output or sample data: delete `data/replays`, re-record, verify on fresh databases in and out of order,
  and scan for keys and labels (`grep -rlE "sk-ant|ANTHROPIC_API_KEY|is_laundering" data/replays`).
- Never put values that depend on run order or time into anything the agent sees (that is why proposal
  ids are hidden from it and tool output uses `LinkedHashMap`, never `Map.of`).
- Live run: `export TRACEWISE_DB=<scratch>/x.db`, then `./tracewise import data/samples/scenarios.csv`,
  `./tracewise monitor run`, `./tracewise --model claude-haiku-5-5 --record investigate ALR-00001`
  (about CAD 0.003 to 0.006 per alert). Build first: `./mvnw -q package -DskipTests`.
- Live API test: `TRACEWISE_LIVE_LLM=1 ./mvnw test -Dtest=AnthropicLiveTest`.
- Models: `claude-haiku-5-5` (USD 0.10 / 0.50 per MTok) for development and recordings;
  `claude-sonnet-5-5` (USD 2 / 10) for real demos. `ModelConfig.of` refuses unknown models.
  LangChain4j stays on 1.21.0 (the graded design's version).
- Runtime API spend so far: about CAD 0.11 (recorded in `docs/ai-usage/ai-tools.md`; add new live runs there).

Shell and secrets:
- NEVER `source` or `.` the `.env` file (a malformed line printed the key on 2026-10-20; that key was
  revoked and replaced). Read it only through `EnvFile`; never print it. Nothing secret goes into the
  Brain vault.
- zsh: `rm -f a.db*` fails when nothing matches; `$VAR="--db x"` is not word-split, so export
  `TRACEWISE_DB` instead.

## 6. Open items (known and deferred, with the reason)

Before the GUI runs work on background threads (all need concurrency the CLI does not have; validators
rejected them as unreachable today):
- One investigation or triage at a time per service: shared `AgentRunner` and cancel flag, findings
  appended by count, `stopRequested` reset at triage start, a cancel landing just after a run ends,
  two runs linking the same alert or expiring each other's proposals. Fix: refuse a second concurrent
  run (or one runner per run) and append findings with a plain INSERT.
- The reviewer identity is the `--role` the session claims; the GUI must not trust a client-side role.

Accepted for now; revisit if they start to matter:
- Triage `--max-cost` is checked between alerts, so it can overshoot by one run (at most CAD 1.00);
  per investigation the agent stops at 12 turns or CAD 1.00 (`AgentLimits.DEFAULT`).
- A reviewer's rejection reason goes into the agent's prompt verbatim (written by the session's own analyst).
- `approvals` ids are COUNT(*)+1 (fine while rows are never deleted); proposals store the command's class
  name, so renaming a command class orphans stored proposals.
- Stored findings have no format version.
- The agent sees flagged transaction ids but not the rule's live thresholds; on a busy account the flagged
  rows can fall outside the 100 rows `account_history` lists. The design's other read tools (window
  aggregates, query cases) are still to build.
- `OpenAiCompatibleLLMClient` from the design is not built: build it or log it as a design change.
- Triage would skip a case reopened later (F08) because it already has a finding.

From the F04 review (2026-10-27), deferred or accepted with evidence:
- Case memory can return a related but different pattern above 0.70 (a fan-out query scored 0.72 against a
  pass-through case; unrelated patterns 0.59 to 0.65). Precedent is shown with its score and the prompt says it is
  not evidence. The design plans retrieval tuning (keyword or hybrid behind `Retriever`) after Stage 3 testing.
- If the agent proposes a close and then concludes ESCALATE, its escalation proposal is refused (one proposal per
  case) and the close expires: the case is left Under Investigation with an ESCALATE finding and nothing waiting.
  The analyst sees the finding; F08's human escalation covers it.
- Re-investigating a decided case (F08 reopen) would let `similar_cases` return the case itself: exclude the case
  in scope when F08 adds reopen. Case summaries show the decision as a prefix, not a separate field.
- Case memory is an indirect prompt-injection channel: past narratives (agent output over imported CSV text) reach
  later runs. Bounded by `ActionScope`, human approval, and the prompt rule that precedent is not evidence.

From the F10 review (2026-10-27), deferred or accepted:
- The assistant shares the investigation's `AgentRunner` (one cancel flag): with the GUI, cancelling an
  investigation would cancel a concurrent question. Part of the concurrency work above.
- Conversation history goes back into the next question as user text, not as earlier assistant turns; each CLI
  `ask` is its own session, so this matters for the GUI chat only. The context (case, account) of earlier
  questions is not stored with them.
- Replayed findings verify indicators against the live search; a cited indicator at the edge of the results
  could drop out on another CPU (the current recordings pass on x86).
- `query_cases` sums the alert totals of a case, so alerts over the same transactions would count twice.
- `ask` failures print only `{"error": ...}` (no trace), unlike `investigate --json`; documented for Stage 3.
