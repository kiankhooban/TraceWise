# Stage 2 handoff (read this first in a new session or after /compact)

Last updated 2026-10-29 (end of week 2), HEAD after commit `93c8480`. Read this file, then `CLAUDE.md` (repo root),
`docs/stage2/PLAN.md` and `docs/stage2/Stage2-Report.md`. Sessions often run from `~/Brain`, where the TraceWise
`CLAUDE.md` is NOT loaded automatically: read it explicitly.

## 1. Where we are

- Stage 1 graded 100/100 (tag `stage1-submitted`). Stage 2 due **2026-11-10**; feature freeze Nov 7; Kian records
  the demo videos Nov 8 to 10 (we write a script per feature first).
- **Functionality is complete.** All 12 features work in the GUI and the CLI; all 9 Stage 1 patterns are in code; the
  three Maven modules of the Stage 1 architecture are in place. 407 tests pass (1 skipped: the opt-in live API test),
  on macOS and on x86 Linux (Docker). Everything is committed and pushed to `main` (no PRs).
- Commits: period 1 (Oct 20 to 26) 44 code commits; period 2 (Oct 27 to Nov 2) 48 code commits so far. Period 3
  (Nov 3 to 9) still needs 5+ meaningful code commits: the UI polish and F10 GUI details provide them naturally
  (never split commits artificially: the handout excludes them).
- Design changes 1 to 49 logged. Collaboration log entries 1 to 16. Reviews 1 to 11 done.
- Stage 2 report draft written (`docs/stage2/Stage2-Report.md`, all 7 handout sections; TODOs for period 3).

| Feature | Main code (core = `core/src/main/java/tracewise/`) | CLI (`cli/.../ui/cli/`) | GUI (`gui/.../ui/gui/`) |
|---|---|---|---|
| F01 Import | `ingest/*` (scenario, IBM, SAML-D formats), `app/ImportService` | `ImportCli` | Alerts screen, Import CSV |
| F02 Monitoring, register, queue | `monitoring/*` (six rules, `WindowedRule`, `RiskScorer`), `app/MonitoringService` | `Monitor/Alerts/ThresholdReports/RulesCli` | Alerts screen, Admin Rules |
| F03 Investigation | `agent/*`, `agent/tools/*` (read tools, `AsOfLedger`, `InvestigationCutoff`), `llm/*`, `app/InvestigationService` | `InvestigateCli` | Cases screen |
| F04 Retrieval, case memory | `retrieval/*`, `agent/tools/{SearchIndicators,SimilarCases}Tool` | `IndicatorsCli`, `CasesCli` | Evidence tab, Admin Knowledge |
| F05 Claim verification | `verification/*` | inside `investigate` | Finding tab claims table |
| F06 Actions, approvals | `actions/*`, `agent/tools/ActionTool` and 6 action tools, `ActionScope` | `ApprovalsCli` | Approvals screen |
| F07 Triage | `app/TriageService`, `app/TriageLimits` | `TriageCli` | Alerts screen (limits, Stop) |
| F08 Cases, audit | `domain/Case`, `cases/state/*`, `app/CaseService`, `audit/*` | `CaseCli`, `AuditCli` | Notes, History, Admin Audit |
| F09 Network | `network/*`, `app/NetworkService` | `NetworkCli` | Network tab (SmartGraph) |
| F10 Assistant | `agent/AssistantTask`, `agent/tools/{QueryCases,QueryAlerts,GetCase}Tool`, `app/AssistantService` | `AskCli` (`--json`) | Assistant panel (3 details missing, see 2) |
| F11 STR reports | `reporting/*`, `actions/{SaveReportDraft,FinaliseReport}Command`, `app/ReportService` | `ReportCli` | Report tab |
| F12 Performance | `metrics/*`, `app/PerformanceService`, `tools/SchemeSampler`, `scripts/evaluate.py` | `MetricsCli` | Admin Performance tab |

Patterns: Facade (`TraceWiseFacade`), Strategy (`DetectionRule`, `TransactionFormat`, `ReportExporter`, `Retriever`,
`AgentTask`), Template Method (`WindowedRule`, `CliCommandBase`), Factory Method (`ActionTool.createCommand`,
`LLMClientFactory`), Command (`ActionCommand`, `CommandDispatcher`, `ApprovalQueue`), State (`CaseState`, 5 states),
Observer (`AgentTrace`, `AgentEventListener`), Adapter (`LangChain4jClient`: `AnthropicLLMClient`,
`OpenAiCompatibleLLMClient`), Decorator (`LLMClientDecorator`: retry, record; `FaultInjectingTool`; `AsOfLedger`).

**Accuracy results** (F12; SAML-D samples; each alert on a fresh database; data cut at the alert, 0 of 225 runs saw a
later alert):

| Held-out set (30 laundering, 30 false alarms, 15 unclear) | Haiku 5.5 | Sonnet 5.5 |
|---|---|---|
| Laundering caught | 28 of 30 | 27 of 30 |
| False alarms cleared | 7 of 30 (tuning set: 15) | 19 of 30 |
| Cost per alert | CAD 0.02 | CAD 0.26 |

Before v4 to v6 of the instructions: 75 of 75 escalated. Report recall and false alarms cleared, not precision on the
30/30 mix (at SAML-D's ~3% base rate precision would be ~0.05 vs 0.03 for the rules alone). IBM data cannot test
judgment (its normal accounts look suspicious); IBM stays for the rules' per-rule precision.

## 2. What is left (period 3, Nov 3 to 9; then Nov 7 to 10)

Kian: "we do the polishing next week". In order:
1. **Discuss the UI look with Kian first** (he wants clean, modern, bank-like, e.g. Wealthsimple, RBC, Bloomberg
   terminal; same pages and structure; nothing against Stage 1). Then polish with the impeccable skill.
2. **F10 assistant GUI details** Stage 1 promised (design change 49): a verification badge per claim, a results table
   for search requests, the selected account as context (today the GUI sends only the open case).
3. **UML**: updated UMLet class diagram (4 figures as in Stage 1; add `AsOfLedger`, `InvestigationCutoff`,
   `SamlDCsvFormat`, `SchemeSampler`, `metrics`, `FinaliseReportCommand`, the three modules) and redrawn SD7 and SD9.
   Generators in `docs/stage1/umlet/`, export with `./render.sh` (UMLet). Check every diagram as a PNG against the
   UML lecture rules (Kian's professor is strict).
4. Final `deep-code-review` of period 3 work; fresh-clone check following the README exactly; secret scan of the
   history (`git log -p | grep -E "sk-ant|ANTHROPIC_API_KEY="`).
5. **Report**: fill the TODOs in `docs/stage2/Stage2-Report.md` (video links, class diagram, period 3 commits and
   tokens, fresh-clone result), then build the PDF `stage2-report` (adapt `docs/stage1/build_pdf.py`).
6. **Demo scripts** per feature (GUI and CLI together), then Kian records the videos (Nov 8 to 10); demo index in
   report section 7. Use Sonnet 5.5 for live demos (the design's default) or the replays.
7. Records at the end: period 3 token counts (`scratchpad tokens.py`, see 5), collaboration entry 17, PLAN progress.

Unanswered or deferred by Kian: PRs vs pushing to main; installers (jpackage plus GitHub Releases) after the deadline;
supervisor-only report finalisation ("if we had time"); Docker for the CLI is optional.

## 3. How Kian wants the work done (standing instructions)

- **Order**: finish open items before starting the next thing. Short, plain answers ("good, bad, promising").
- **Every change set is reviewed** with `deep-code-review` before it is called done; one independent validator per
  finding; confirmed findings fixed test-first; re-record `data/replays` if the agent's requests changed.
- **NO OTHER AI MODELS** (Kian, 2026-10-29): reviews use Claude reviewer and validator agents only; never Codex,
  Gemini, `agy` or anything else. No Workflow tool unless Kian opts in.
- **Skills**: superpowers TDD (watch each test fail first), ponytail level full (never ultra; the Stage 1 design
  counts as requested, so its patterns are never cut), context7 for library APIs.
- The Stage 1 design is the spec; nothing against it in a large way. Every deviation goes in
  `docs/stage2/design-changes.md` immediately. When in doubt, check `docs/stage1/Stage1-Design-Report.md`.
- Commit after each working unit and push; end messages with the session's `Claude-Session:` trailer.
- No em dashes anywhere. Discuss before large or outward-facing actions. Kian owns the code. Spend on the API is
  fine when it serves measurement (Kian), but say the estimate first.
- Records: `docs/ai-usage/collaboration-log.md`, `docs/ai-usage/ai-tools.md` (tools, token use per period, API
  spend), `docs/stage2/PLAN.md` (status, progress log), `docs/stage2/design-changes.md`.

## 4. The working loop

```text
read design -> tests first (RED) -> code (GREEN) -> commit and push per unit -> live check with Haiku if the
agent changed -> deep-code-review (Claude reviewers) -> one validator per finding -> fix confirmed ones test-first
-> re-record data/replays if prompt, tools or tool output changed -> full suite -> records -> commit and push
```

How reviews are run (review 11 is the template; run dir `/tmp/compound-engineering/ce-code-review/20261029-143540-4d12e291`):
- Stage `full.diff` and `files.txt` in a run dir. Build one `shared-contract.md` from
  `~/.claude/skills/deep-code-review/references/subagent-template.md` (the part from `<scope-rules>` to before
  `<pr-context>`, with diff-scope rules and the findings schema filled in) plus a review-context block (intent, paths
  to the diff). Dispatch each reviewer with "Read shared-contract.md, your persona file is <path>, your name is X".
  Correctness, security and adversarial on the session model; the rest on Sonnet.
- Merge: dedupe, promote cross-reviewer agreement, drop anchor-50 non-P0, route testing and maintainability-only
  advisories to testing gaps and residual risks. Validators: one per surviving finding from `validator-template.md`.
- Pitfall: quiet runs (`./mvnw -q`) hide compile errors; count results from `*/target/surefire-reports` after
  deleting old reports (a stale root `target/` inflated counts by 9 until 2026-10-29).

## 5. Build, run, conventions, pitfalls

Build and run:
- Modules: `core/` (tracewise-core, all logic, no UI), `cli/` and `gui/` (front ends, call only the facade). Parent
  pom at the root; the core publishes a test jar; surefire runs from the repo root so `data/` paths resolve.
- `./mvnw -q install -DskipTests` builds `cli/target/tracewise.jar` and `gui/target/tracewise-gui.jar`;
  `./mvnw -q install` runs all tests. Launch: `./tracewise <command>` and `./tracewise-gui`.
- GUI screenshots without a screen: `TRACEWISE_SNAPSHOT=<dir> [TRACEWISE_SNAPSHOT_INVESTIGATE=ALR-00001]
  [TRACEWISE_SNAPSHOT_ASK="..."] TRACEWISE_LLM=replay TRACEWISE_MODEL=claude-haiku-5-5 ./tracewise-gui` saves a
  PNG of every tab, then exits (Read the PNGs).
- x86 check: copy with `git ls-files -c | grep -v '^\.env$' | rsync -a --files-from=- . <dir>/` (never `.env`), then
  `docker run --rm --platform linux/amd64 -v <dir>:/w -w /w maven:3.9-eclipse-temurin-21 mvn -B -q install`.
  Docker Desktop must be running (`open -a Docker`); Maven downloads sometimes fail mid-handshake: just rerun.

Code:
- Packages (core): `domain, ingest, monitoring, persistence, audit, app, llm, agent, agent.tools, actions,
  cases.state, verification, retrieval, reporting, network, metrics, tools`; CLI `ui.cli`; GUI `ui.gui`.
- New user operations: service in `app`, method on `TraceWiseFacade`, wiring in `TraceWiseBootstrap.create`.
  `investigationTools(...)` is the single place the agent's tools are registered.
- CLI commands extend `CliCommandBase` (`execute()`, optional `print()`; `--json` automatic), listed in `TraceWiseCli`.
- Persistence: `Database` plus `Jdbc.query/update`; schema `core/src/main/resources/tracewise/schema.sql` (CREATE IF
  NOT EXISTS, no migrations). `db.inTransaction` nests. Money from JSON: `ExactJson.mapper()`.
- Agent safety: `PermissionPolicy` (agent alone: priority, link, watchlist, task; the rest needs a human).
  `ActionScope.begin(caseId, alertId)` limits actions to the case and names the alert under investigation.
- No hindsight: while an investigation runs, the read tools see the data only up to the investigated alert's last
  flagged transaction (`InvestigationCutoff` computes it once per alert; `AsOfLedger` applies it; `linked_alerts`
  hides alerts with later transactions). The assistant (no investigation) sees everything.
- LLM providers: `anthropic` (key `ANTHROPIC_API_KEY`), `openai` (any OpenAI-compatible API: `OPENAI_BASE_URL`,
  `OPENAI_API_KEY` in `.env`), `replay`. Models with prices: `claude-haiku-5-5` (USD 0.10 / 0.50), `claude-sonnet-5-5`
  (2 / 10, default), `claude-opus-5-5` (4 / 20). Non-Claude models cost 0 and the CLI and GUI say cost limits do not
  apply. LangChain4j stays on 1.21.0.
- GUI: every facade call goes through `GuiWorker` (one background thread); views get a `Gui` context.
- Retrieval: bge-small loads lazily; unit tests use `WordCountEmbeddingModel`. Minimum score 0.70.
- Parties outside the bank: account `CASH` or prefix `EXT-`. Session role: `--role` or `TRACEWISE_ROLE`.

Tests:
- `@TempDir` real SQLite. Shared doubles: `monitoring/InMemoryLedger`, `agent/AgentRunnerTest.ScriptedModel`,
  `actions/ActionsFixture`, `agent/tools/ToolRegistryTest.EchoTool`, `FaultInjectingTool` for tool failures.
- Sample data: `ScenarioGenerator.generate(42)`; neutral ids (`C1037`, `EXT-2101`, `TX-00001`). Expected monitoring:
  4 threshold reports; alerts ALR-00001 R3, ALR-00002 R4, ALR-00003 R5, ALR-00004 R6. Never put scheme names in
  anything the agent can read.

Replays and live runs:
- `data/replays` (35 files, Haiku 5.5, recorded 2026-10-29 with v6): the four sample alerts (triage), seven assistant
  questions (`CommittedReplaysTest.QUESTIONS`, each in its own process after the triage), report drafts for CAS-00001,
  2 and 4 after approving the three escalations. On the sample data v6 escalates three schemes and leaves the
  fan-out one (ALR-00003) inconclusive. Retrieval results are left out of replay matching (ARM vs x86 scores).
- Re-record after ANY change to prompt, tools, tool output or sample data: `git rm -r data/replays`; fresh DB:
  `./tracewise --db X --model claude-haiku-5-5 import data/samples/scenarios.csv`, `monitor run`, `--record triage`;
  each question `--record ask --json "<q>"`; `--role SUPERVISOR approvals approve APR-0000N` for the three; `--record
  report draft CAS-00001` (and 2, 4); scan `grep -rlE "sk-ant|ANTHROPIC_API_KEY|is_laundering" data/replays`.
- Live API test: `TRACEWISE_LIVE_LLM=1 ./mvnw test -pl core -Dtest=AnthropicLiveTest`.
- TraceWise's own API spend so far: about CAD 64 (table in `docs/ai-usage/ai-tools.md`).

Evaluation (F12), scratchpad `/private/tmp/claude-501/-Users-kiankhooban-Brain/529e6224-32b5-4afd-9767-3f9d109d47e5/scratchpad/`
(may be gone in a new session; recreate from the steps):
- SAML-D: Kaggle `berkanoztas/synthetic-transaction-monitoring-dataset-aml` (CC BY-NC-SA 4.0), `samld/SAML-D.csv`.
  Amount is GBP for every row. Samples: `java -cp cli/target/tracewise.jar tracewise.tools.SchemeSampler samld <csv>
  <out.csv> 40 1500 <part> 42` (parts 1 tuning, 2 held-out; same seed and sizes or they are not disjoint), import,
  monitor, then a fixed alert list (30 laundering, 30 clean, 15 unclear) in `sml/tune2-alerts.txt`, `test2-alerts.txt`.
- Run: `TRACEWISE_JAR=<jar> python3 -I scripts/evaluate.py <db> <alerts.txt> <outdir> <model> 3` (resumes only
  alerts without a finding; refuses another jar or model). Score: `java -jar <jar> --db <outdir>/merged.db metrics
  --model <model>`; per class `sml/../f12/tools/classes.py <outdir>`; hindsight check `scratchpad/leak.py
  <merged.db> <source.db>`. Results dirs: `sml/ev-a7`, `test-a7` (Haiku), `test-sonnet`.
- Development token counts: `python3 -I scratchpad/tokens.py` (sums the TraceWise session log 529e6224 and its
  subagents by period; add a period 3 range).

Shell and secrets:
- NEVER `source` or `.` the `.env` file. Read it only through `EnvFile`; never print it. Nothing secret goes into the
  Brain vault. Never copy `.env` into Docker.
- zsh: globs that match nothing fail; a variable holding "cmd args" is not word-split (use a shell function).

## 6. Open items (known and deferred, with the reason)

Concurrency (one investigation, triage or question at a time per process; `ActionScope` and the cutoff hold one
run): two simultaneous runs in one process would share the scope, the runner's cancel flag and the cutoff. The GUI
runs everything through one worker thread, and `evaluate.py` uses one process per alert, so it is not reachable today.

Accepted for now:
- Triage `--max-cost` is checked between alerts (can overshoot by one run, at most CAD 1.00); per run the agent stops
  at 12 turns or CAD 1.00.
- `approvals` ids are COUNT(*)+1; proposals store the command class name; stored findings have no format version.
- Triage skips a case reopened later (F08) because it already has a finding.
- The requester can approve their own report finalisation (Kian deferred a different-person rule).
- `linked_alerts` still filters by an alert's current status and case, which can reflect decisions made after the
  cutoff (pre-existing; eval databases are fresh per alert, so results are unaffected).
- Recording an `openai` run into `data/replays` could overwrite a Claude recording with an identical first request
  (the replay key has no model name). Record other providers into a separate `--replays` folder.
- `OPENAI_BASE_URL` over plain http would send the key in cleartext (operator configuration).
- Testing gaps from review 11: `OpenAiCompatibleLLMClient` HTTP errors and text-only replies; `linked_alerts` on the
  assistant path; the facade's `retrieved*` delegation; the bootstrap's `OPENAI_*` lookup.
- Agent-native gap: the scored passages a past run retrieved show in `investigate --json` (trace) and the GUI
  Evidence tab, but not in `case show` later.
- Case memory can return a related but different pattern above 0.70; precedent is labelled as not evidence.
- Case memory is an indirect prompt-injection channel; bounded by `ActionScope`, human approval and the prompt.
- F10: conversation history goes back as user text; `ask` failures print only `{"error": ...}`; `query_cases` sums
  alert totals per case (overlapping alerts count twice).
- F11: rejected-claim detection is exact text only; exports are not written atomically.
- Review 8 and 9 notes: the network's 50-counterparty cap is shared across hops; metrics load every case (fine for
  hundreds); recall covers only alerted laundering; state the sample when quoting numbers.

Real data notes: IBM HI-Small (5.08M rows) imports in 12.5 minutes (582,422 rows rejected for BTC, RUB, SAR, ILS with
no Bank of Canada rate); monitoring the whole file passes 100,000 alerts (R4 is very noisy on IBM). A busy IBM
account cost CAD 0.08 to investigate.
