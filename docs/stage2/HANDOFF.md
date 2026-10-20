# Stage 2 handoff (read this first after /compact or in a new session)

Last updated 2026-10-20, after commit `e30eb8e`. Read this file, then `CLAUDE.md` (repo root) and
`docs/stage2/PLAN.md`. Sessions often run from `~/Brain`, where the TraceWise `CLAUDE.md` is NOT
loaded automatically: read it explicitly.

## 1. Where we are

- Stage 1 graded 100/100 (tag `stage1-submitted`). Stage 2 due **2026-11-10**; feature freeze Nov 7;
  Kian records the demo videos Nov 8 to 10 (we write a script per feature in week 3).
- Period 1 (Oct 20 to 26) plan is **complete** on Oct 20: 53+ commits pushed (5 needed per period).
  Periods 2 (Oct 27 to Nov 2) and 3 (Nov 3 to 9) still each need 5+ meaningful code commits.
- 181 tests pass, 1 skipped (the opt-in live API test). Design changes 1 to 20 logged.

| Feature | State | Main code |
|---|---|---|
| F01 Import | CLI done | `ingest/*`, `app/ImportService`, `ui/cli/ImportCli` |
| F02 Monitoring, threshold register, alert queue | CLI done | `monitoring/*`, `app/MonitoringService`, `ui/cli/{Monitor,Alerts,ThresholdReports,Rules}Cli` |
| F03 Investigation | CLI done | `agent/*` (`AgentRunner`, `AgentTrace`, `InvestigationTask`), `agent/tools/*`, `llm/*`, `app/InvestigationService`, `ui/cli/InvestigateCli` |
| F05 Claim verification | done (inside investigate) | `verification/*`, `agent/Claim`, `agent/TotalClaim` |
| F06 Actions and approval queue | CLI done | `actions/*`, `agent/tools/ActionTool` and 6 action tools, `agent/tools/ActionScope`, `ui/cli/ApprovalsCli` |
| F07 Triage | CLI done | `app/TriageService`, `app/TriageLimits`, `agent/TriageReport`, `ui/cli/TriageCli` |
| F08 Case management, audit | partly: case states, storage, audit of every step and action | `domain/Case`, `cases/state/*`, `persistence/SqliteCaseRepository`, `app/CaseService` (only `getCase`) |
| F04, F09, F10, F11, F12 | not started | |
| GUI (JavaFX) | not started | every feature needs its screen; the videos must show GUI and CLI |

All nine designed patterns are in code: Facade (`TraceWiseFacade`), Strategy (`DetectionRule`,
`TransactionFormat`), Template Method (`WindowedRule`, `CliCommandBase`), Adapter (`LangChain4jClient`),
Decorator (`LLMClientDecorator`: retry, record), Factory Method (`LLMClientFactory`,
`ActionTool.createCommand`), Observer (`AgentTrace` and `AgentEventListener`, `AuditLog` listens),
State (`CaseState` and 5 states), Command (`ActionCommand`, `CommandDispatcher`, `ApprovalQueue`).

## 2. Next tasks, in order

1. **F04 retrieval**: FINTRAC indicators and similar past cases with local embeddings
   (bge-small-en-v1.5 quantized, in-process through LangChain4j). The artifact
   `langchain4j-embeddings-bge-small-en-v15-q` is in `~/.m2` but NOT yet in `pom.xml`: add it at a
   version matching LangChain4j 1.21.0 (check with context7). Design: F04 sections of
   `docs/stage1/Stage1-Design-Report.md` (around lines 502 and 1889), `model.iuml` package
   `tracewise.retrieval` (around line 529: `Retriever`, `EmbeddingRetriever`, `CaseMemory`), tools
   `SearchIndicatorsTool` and `SimilarCasesTool`. Adding tools changes the agent's requests, so
   re-record `data/replays` afterwards.
2. **F10** `tracewise ask --json` (SD6 in `docs/stage1/umlet/gen_sequence_diagrams.py`; `AssistantService`,
   `AssistantTask`, `ConversationMemory`).
3. **JavaFX shell** with Alerts, Case and Approvals screens, then **F08** case commands (`case show`,
   notes, reopen, human close and escalate submitted through `CommandDispatcher` as a human actor).
4. Week 3: F09 network view, F11 STR draft and export (includes `SaveReportDraftCommand`), F12 dashboard,
   updated UMLet class diagram, `stage2-report` PDF, demo scripts.

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
- Three reviews so far (2026-10-20): F03/F05, F06, F07. See collaboration log Entries 7 to 9.

## 5. Conventions and pitfalls

Code:
- Packages: `domain, ingest, monitoring, persistence, audit, app, llm, agent, agent.tools, actions,
  cases.state, verification, ui.cli, tools`.
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
- `data/replays` holds Claude Haiku 5.5 recordings of the four sample alerts (with actions). They replay
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
- Runtime API spend so far: about CAD 0.06 (recorded in `docs/ai-usage/ai-tools.md`; add new live runs there).

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
