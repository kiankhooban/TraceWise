# Stage 2 plan and status

Source: `Project-Stage2-Instruction.pdf` (course handout, read 2026-10-20). Implementation period
2026-10-20 to 2026-11-10; submission due 2026-11-10.

## What Stage 2 requires
- Working GUI and CLI, all 12 Stage 1 features accounted for (implemented, or a documented change),
  at least 5 distinct patterns evident in code, real LLM integration and agent behaviour.
- At least 5 meaningful code commits pushed in each period: Oct 20-26, Oct 27-Nov 2, Nov 3-9
  (missing any period: -5 points).
- `stage2-report` (PDF) in the repo, sections: 1 overview and status, 2 feature-to-implementation
  mapping (with video link per feature), 3 implemented patterns plus an updated UML class diagram,
  4 changes and rationale, 5 AI tools (with token and money cost) and collaboration log,
  6 development and verification summary (weekly commit summary with links), 7 demonstration index.
- A demo video segment for every implemented feature; together they show both GUI and CLI.
- README with prerequisites, install, local configuration (`.env` from `.env.example`), GUI and
  CLI launch commands, example usage. Safe sample data committed.

## Schedule (revised 2026-10-20: front-loaded to protect the deadline)
| Period | Code | Records |
|---|---|---|
| 1: Oct 20-26 | Done: skeleton, F01, F02, CLI, sample data, EnvFile, LLMClient and Anthropic adapter. Next: retry and replay decorators, agent loop, trace and listeners, tool registry and read tools, claim verifier, `investigate` CLI (F03, F05); then commands, permission tiers and approvals (F06) and triage (F07) | design-changes log, collaboration entries |
| 2: Oct 27-Nov 2 | Retrieval with embeddings (F04), `ask --json` (F10), JavaFX shell with Alerts, Case and Approvals screens, case states and audit screen (F08) | collaboration entries |
| 3: Nov 3-9 | Network view (F09), STR draft and export (F11), dashboard (F12), remaining GUI, fixes. Feature freeze Nov 7 | updated UMLet class diagram, report draft, demo scripts |
| Nov 8-10 | Fresh-clone README check, secret scan of history | Kian records videos; report PDF and demo index |

## Revised schedule (agreed with Kian, 2026-10-27)
Do almost all the work in period 2 so there is time to recover if something goes wrong:
- Now to Nov 2: JavaFX GUI shell with the concurrency fixes, every working feature on screen, F08 case
  management, F11 STR drafts and export, F09 network view, report draft.
- Nov 3 to 6 (period 3): F12 performance dashboard, a real self-contained feature (metrics, IBM sample,
  CLI, GUI screen, tests: naturally 5 or more commits), plus fixes from the final review and the
  fresh-clone check. Commits are never split artificially: the handout excludes them.
- Nov 7 to 10: feature freeze, Kian records the videos, report finished. Submission Nov 10.

## Revised schedule 2 (agreed with Kian, 2026-10-28)
Finish all functionality this week; spend period 3 on a UI and UX polish (real commits), then freeze.
- Oct 28 to 30: F12 with the IBM sampler the Stage 1 report describes (whole planted schemes plus the full history of
  their accounts plus random other accounts), PerformanceService and metrics, CLI `metrics`, GUI Performance screen;
  deep-code-review.
- Oct 31 to Nov 2: baseline numbers with Haiku 5.5, then improvements the numbers point to (rule calibration, which
  the design plans for R3 to R6; the agent's instructions; consistency across runs). Tune on one IBM sample and report
  on a different, held-out sample. Then Sonnet 5.5 (the design's default model) on the same held-out sample for the
  comparison. Stage 2 report draft.
- Nov 3 to 7 (period 3): UI and UX polish of the existing screens (no change to the pages or their structure; clean,
  modern, bank-like; design to be discussed with Kian first, audit with the impeccable skill), final review,
  fresh-clone check, class diagram.
- Nov 7 to 10: freeze, demo scripts, Kian records the videos, report PDF.
Rule: nothing may go against the Stage 1 design in a large way; any deviation is small and logged.

| Feature | Status | Implemented in | Video |
|---|---|---|---|
| F01 Transaction Data Import | Working (CLI); GUI pending | `ingest/*`, `app/ImportService`, `persistence/SqliteTransactionRepository`, `ui/cli/ImportCli` | |
| F02 Rule-Based Monitoring, Threshold Reporting and Alert Queue | Working (CLI); GUI pending | `monitoring/*` (six rules, `WindowedRule`, `RiskScorer`), `app/MonitoringService`, alert, register and rule-config repositories, `ui/cli/{Monitor,Alerts,ThresholdReports,Rules}Cli` | |
| F03 Autonomous Alert Investigation | Working (CLI); GUI pending | `agent/*` (`AgentRunner`, `AgentTrace`, `InvestigationTask`), `agent/tools/*` (`ToolRegistry`, read tools), `llm/*` (retry, record, replay decorators, factory), `app/InvestigationService`, `ui/cli/InvestigateCli` | |
| F04 Indicator Retrieval and Case Memory | Working (CLI and agent tools); GUI Evidence tab and Knowledge screen pending | `retrieval/*` (`Retriever`, `EmbeddingRetriever`, `CaseMemory`, `IndicatorCorpus`), `agent/tools/{SearchIndicators,SimilarCases}Tool`, `resources/tracewise/fintrac-indicators.tsv`, `ui/cli/{Indicators,Cases}Cli` | |
| F05 Claim Verification | Working (CLI, inside `investigate`) | `verification/*` (`ClaimVerifier`, `VerificationReport`), `agent/Claim`, `agent/TotalClaim` | |
| F06 Agent Actions and Approval Queue | Working (CLI); GUI pending; report-draft action comes with F11 | `actions/*` (`ActionCommand` and six commands, `CommandDispatcher`, `PermissionPolicy`, `ApprovalQueue`, `PendingAction`), `agent/tools/ActionTool` and six action tools, `persistence/SqliteApprovalRepository`, `ui/cli/ApprovalsCli` | |
| F07 Autonomous Queue Triage | Working (CLI); GUI pending | `app/TriageService`, `app/TriageLimits`, `agent/TriageReport`, `ui/cli/TriageCli` | |
| F08 Case Management and Audit Trail | Working (CLI and GUI) | `domain/Case`, `cases/state/*`, `persistence/SqliteCaseRepository`, `audit/AuditLog` | |
| F09 Money-Flow Network View | Working (CLI and GUI Network tab) | `network/*` (`MoneyFlowGraph`, `AccountNode`, `FlowEdge`), `app/NetworkService`, `ui/cli/NetworkCli`, `ui/gui/NetworkView`, `resources/tracewise/smartgraph.*` | |
| F10 Natural-Language Analyst Assistant | Working (CLI, `ask --json`); GUI Assistant panel pending | `agent/{AssistantTask,AssistantAnswer,AssistantContext,ConversationMemory,AgentJson}`, `agent/tools/{QueryCases,QueryAlerts,GetCase}Tool`, `app/AssistantService`, `ui/cli/AskCli` | |
| F11 Suspicious Transaction Report Drafting and Export | Working (CLI and GUI Report tab) | `reporting/*` (`StrReport`, `ReportExporter`, `HtmlReportExporter`, `JsonReportExporter`), `actions/{SaveReportDraft,FinaliseReport}Command`, `app/ReportService`, `ui/cli/ReportCli`, `ui/gui/ReportTab` | |
| F12 Agent Performance Dashboard | Working (CLI and GUI Performance tab) | `metrics/*` (`PerformanceMetrics`, `Measure`, `MetricsFilter`, `RulePrecision`, `AlertLabel`, `MetricRow`, `MetricsCsv`), `app/PerformanceService`, `ui/cli/MetricsCli`, `ui/gui/AdminView` (Performance tab), `tools/IbmSampler` | |

## Progress log
- 2026-10-20: period 1 opened. Build skeleton, F01 import (both formats, rejection reasons,
  duplicates, rollback, audit), F02 (six rules, risk scoring, alert queue, Threshold Report
  Register, persisted rule configs with supervisor-only changes), CLI for both, scenario generator
  and sample data. 67 tests passing. 15 code commits in period 1.
- 2026-10-20 (later): LLM retry, record and replay decorators and the client factory; tool registry
  with argument validation; account history and counterparty tools; the agent loop with trace,
  listeners, turn and cost limits; claim verification; cases with the State pattern; the
  `investigate` command. Live check on the sample data with Haiku 5.5: all four alerts escalated,
  every cited claim confirmed, CAD 0.012 in total. 134 tests passing. Design changes 7 to 13.
- 2026-10-20 (review): `deep-code-review` of the F03/F05 work (11 reviewers plus Codex, each finding
  validated). Fixed: scheme names in sample ids, totals with repeated or no transactions, a parser
  crash, replay-breaking key order, unrecorded run failures, unlimited tool calls per response
  (design change 14). Recordings of the four sample alerts committed in `data/replays`. 142 tests.
- 2026-10-20 (F06): cancel race fixed and tested; agent actions with permission tiers (Command, Factory
  Method), approval queue with approve, reject and expiry, `approvals` CLI. Live check: the agent set a
  priority and proposed escalation; approving it escalated the case; all audited. 163 tests. Design
  changes 15 and 16.
- 2026-10-20 (review 2): fixes for approval widening, agent action scope, fail-safe policy, decision
  time (design change 17). Recordings re-made with actions; replay checked in any order. 167 tests.
- 2026-10-20 (F07): proposals from runs without a finding expire and re-runs see earlier decisions
  (design change 18); triage with alert and cost limits, stop, failure handling and summary, `triage`
  CLI (design change 19). A full triage of the sample alerts replays offline. 178 tests.
- 2026-10-20 (review 3): triage re-checks each alert, stops after 3 failures in a row, counts actions
  from every run (design change 20). 181 tests. Period 1 commits pushed: well over 5.
- 2026-10-27 (F04): period 2 opened. FINTRAC indicator corpus (110 indicators, verbatim), local
  bge-small-en-v1.5 retrieval behind the `Retriever` strategy, case memory fed by approved closes and
  escalations, `search_indicators` and `similar_cases` tools, `indicators search` and `cases similar`.
  Live check with Haiku 5.5 on the four sample alerts: FINTRAC-vocabulary queries, indicators cited word
  for word, every claim confirmed; recordings re-made. Fixes from the live check: similar cases matched by
  indicators, proposals that contradict the agent's own verdict expire. 216 tests. Design changes 21 to 24.
- 2026-10-27 (review 4): `deep-code-review` of F04 (ten reviewers plus Codex, each finding validated):
  replays explained and guarded by a test, indicators checked against search results, case memory retries
  failed loads and never changes inside a decision's transaction. 223 tests. Design change 25.
- 2026-10-27 (checks, F10): checked the replays in an x86 Linux container: they failed there because the
  embedding scores differ from Apple Silicon in the third decimal, so retrieval results are now left out of
  replay matching (also fixes replays after approvals); recordings re-keyed without new model calls; the agent
  loop now runs only the tools a task offers. F10: `query_cases`, the read-only assistant and `tracewise ask
  [--json]`; five questions recorded live with Haiku 5.5 and replayed in the build on both CPU types.
  245 tests. Design changes 26 to 28.
- 2026-10-27 (review 5): `deep-code-review` of the replay fix and F10: answer kind in `ask --json`, `query_alerts`
  and `get_case`, "open" cases match undecided ones, questions audited with the user. Questions re-recorded.
  254 tests. Design change 29.
- 2026-10-27 (GUI, F08, review 6): JavaFX GUI for F01 to F08 and F10 (Alerts, Cases, Approvals, Admin,
  Assistant) on one background worker, screenshot mode for checking; F08 notes, analyst close and reopen,
  history, audit filters and CSV export, CLI `case` and `audit export`. Review fixes: close and reopen target,
  queued cancel and stop, shutdown order, linked-alert case, notes for the assistant. 283 tests. Changes 30 to 32.

- 2026-10-27 (F11, review 7): suspicious transaction reports: the model drafts the grounds for suspicion from the
  confirmed facts, the analyst edits, finalisation waits in the approval queue on an escalated case and is refused
  while a field is empty or a rejected claim remains, final reports export as HTML and JSON. CLI `report` and the
  GUI Report tab. Narratives for the three escalated sample cases recorded live. Review fixes: stale finalisation
  requests, redraft only with `--replace`, drafts from an older finding, the unverified summary labelled. 315 tests.
  Design changes 33 and 34.
- 2026-10-28 (real data, F09, review 8): IBM's HI-Small file (5.08M rows) through the pipeline: the import ran out of
  memory, so it now streams in chunks in one transaction (the full file imports in 12.5 minutes); the
  `window_aggregates` read tool from the design (busy accounts); F09 money-flow network (service, `network`, Network
  tab). Review fixes: rollback on an Error, bounded rejected rows, last-hop edges, key lookups. The Codex cross-model
  pass had silently not run since the GUI review (usage limit until Nov 19); records corrected. 342 tests.
  Design changes 35 to 37.
- 2026-10-28 (F12, review 9): performance metrics (precision and recall against labels, a stricter recall, verdict
  counts, verification, turns, cost, failures, overrides, per-rule precision; per model), `metrics` CLI with CSV, the
  Performance tab, and the IBM sampler with disjoint tuning and held-out parts. Live check: Haiku on 8 IBM alerts,
  precision 0.625 (5 of 8), all escalated, CAD 0.17. Review fixes: held-out part shared rows with the tuning part,
  verdict counts in every output, replays measured apart, failed-only models shown. 369 tests. Design changes 38 to 40.
## Tooling (installed 2026-10-20)
- `jdtls-lsp` plugin with `jdtls` 1.63.0 (Homebrew): Java diagnostics and navigation.
- `ponytail` 5.3.2: level `full`; cost ledger on, webhook off, task names not sent.
- `session-report`: token and cost reports from local transcripts, for the AI tools table.
- Already present: superpowers, context7, `/code-review`, `deep-code-review`.
- Considered and not installed: pstack (overlaps superpowers and ponytail, re-opens the graded design
  through per-feature design exploration, depends on Cursor-only features).
