# TraceWise: Stage 2 Implementation Report

| | |
|---|---|
| **Course** | EECS3311 Software Design, Fall 2026, York University |
| **Author** | Kian Khooban |
| **Repository** | https://github.com/kiankhooban/TraceWise |
| **Status of this file** | DRAFT (2026-10-29). Items marked TODO are completed in period 3 (Nov 3 to 9). |

This report follows the Stage 2 instructions: 1 overview and status, 2 feature-to-implementation mapping, 3
implemented patterns and the updated class diagram, 4 changes and rationale, 5 AI tools and collaboration, 6
development and verification summary, 7 demonstration index.

---

# 1. Overview and status

TraceWise is a Java 21 application for anti-money-laundering (AML) teams at a Canadian bank. Deterministic
rules based on FINTRAC guidance raise alerts and keep the Threshold Report Register. An LLM agent (Anthropic
Claude through LangChain4j) then investigates each alert with read tools, retrieval of FINTRAC indicators and
memory of past cases, and writes a finding. A claim verifier checks every figure the finding cites against the
data before anyone sees it. The agent's actions are permission-tiered: low-risk actions run, while closing or
escalating a case waits for a human decision. It has a JavaFX GUI and a picocli CLI over one facade.

**Status.** All 12 Stage 1 features are implemented and run in both the GUI and the CLI. All 9 design patterns
of the Stage 1 report are in the code. The three Maven modules of the Stage 1 architecture are in place:
`tracewise-core` holds all logic and depends on no user interface, and `tracewise-cli` and `tracewise-gui` call
it only through `TraceWiseFacade`. 407 automated tests pass on macOS (Apple Silicon) and on x86 Linux in Docker.

**Measured results** (F12, on SAML-D samples; each alert investigated on a fresh copy of the data, with the data
cut at the alert so the agent has no hindsight):

| Held-out set: 30 laundering, 30 false-alarm alerts | Claude Haiku 5.5 | Claude Sonnet 5.5 (default) |
|---|---|---|
| Laundering alerts caught (escalated) | 28 of 30 | 27 of 30 |
| False alarms cleared (dismissed) | 7 of 30 | 19 of 30 |
| Failed runs | 0 of 75 | 0 of 75 |
| Cost per alert | about CAD 0.02 | about CAD 0.26 |

Before the investigation instructions were reworked, the agent escalated 75 of 75 alerts. Precision on a
balanced 30/30 set is not a real-world figure: at SAML-D's base rate (about 3% of alerts are laundering) it would
be about 0.05 with Haiku, against 0.03 for the rules alone. Details: design changes 41 to 45 and 49, and the
collaboration log entries 15 and 16.

**Not done or limited:** the stretch goals in `docs/ROADMAP.md` are not started. TODO: remaining limitations
from the final review (section 6).

# 2. Feature-to-implementation mapping

Paths are under `core/src/main/java/tracewise/` unless they start with `cli/` or `gui/` (short for
`cli/src/main/java/tracewise/ui/cli/` and `gui/src/main/java/tracewise/ui/gui/`).

| Feature | Status | Main classes | CLI | GUI | Video |
|---|---|---|---|---|---|
| F01 Transaction Data Import | Done | `ingest/` (`TransactionFormat` and three formats, `CurrencyConverter`), `app/ImportService` | `cli/ImportCli` | Alerts screen, Import CSV | TODO |
| F02 Rule-Based Monitoring, Threshold Reporting, Alert Queue | Done | `monitoring/` (six rules, `WindowedRule`, `RiskScorer`), `app/MonitoringService` | `cli/{Monitor,Alerts,ThresholdReports,Rules}Cli` | Alerts screen, Admin Rules tab | TODO |
| F03 Autonomous Alert Investigation | Done | `agent/` (`AgentRunner`, `AgentTrace`, `InvestigationTask`), `agent/tools/` (read tools, `AsOfLedger`), `llm/`, `app/InvestigationService` | `cli/InvestigateCli` | Cases screen | TODO |
| F04 Indicator Retrieval and Case Memory | Done | `retrieval/` (`Retriever`, `EmbeddingRetriever`, `CaseMemory`, `IndicatorCorpus`), `agent/tools/{SearchIndicators,SimilarCases}Tool` | `cli/{Indicators,Cases}Cli` | Evidence tab, Admin Knowledge tab | TODO |
| F05 Claim Verification | Done | `verification/` (`ClaimVerifier`, `VerificationReport`) | inside `investigate` | Finding tab claims table | TODO |
| F06 Agent Actions and Approval Queue | Done | `actions/` (`ActionCommand`, six commands, `CommandDispatcher`, `PermissionPolicy`, `ApprovalQueue`), `agent/tools/ActionTool` | `cli/ApprovalsCli` | Approvals screen | TODO |
| F07 Autonomous Queue Triage | Done | `app/TriageService`, `app/TriageLimits`, `agent/TriageReport` | `cli/TriageCli` | Alerts screen, triage with limits and Stop | TODO |
| F08 Case Management and Audit Trail | Done | `domain/Case`, `cases/state/`, `audit/AuditLog`, `app/CaseService` | `cli/{Case,Audit}Cli` | Notes and History tabs, Admin Audit tab | TODO |
| F09 Money-Flow Network View | Done | `network/`, `app/NetworkService` | `cli/NetworkCli` | Network tab (SmartGraph) | TODO |
| F10 Natural-Language Analyst Assistant | Done | `agent/AssistantTask`, `agent/tools/{QueryCases,QueryAlerts,GetCase}Tool`, `app/AssistantService` | `cli/AskCli` (`--json`) | Assistant panel | TODO |
| F11 STR Drafting and Export | Done | `reporting/` (`StrReport`, `ReportExporter`, HTML and JSON exporters), `actions/{SaveReportDraft,FinaliseReport}Command`, `app/ReportService` | `cli/ReportCli` | Report tab | TODO |
| F12 Agent Performance Dashboard | Done | `metrics/`, `app/PerformanceService`, `tools/SchemeSampler`, `scripts/evaluate.py` | `cli/MetricsCli` | Admin Performance tab | TODO |

# 3. Implemented patterns and the updated class diagram

All nine patterns of the Stage 1 report are implemented where the report placed them.

| Pattern | Problem it solves here | Participants in the code |
|---|---|---|
| Strategy | Interchangeable rules, file formats, exporters, retrieval and agent tasks | `DetectionRule` (six rules), `TransactionFormat` (scenario, IBM, SAML-D), `ReportExporter` (HTML, JSON), `Retriever`, `AgentTask` (investigation, assistant) |
| Template Method | Fixed steps with one varying step | `WindowedRule.evaluate` (final) with `isMatch` per rule; `CliCommandBase.call` (final) with `run` per command |
| Factory Method | Each agent tool creates its own command | `ActionTool.createCommand` in six action tools |
| Command | Every change to a case goes through one dispatcher that checks permissions, audits and queues for approval | `ActionCommand` and its commands, `CommandDispatcher`, `PermissionPolicy`, `ApprovalQueue` |
| State | Legal case transitions | `CaseState` with Open, UnderInvestigation, PendingApproval, Escalated, Closed in `cases/state/` |
| Observer | The GUI and the audit log follow an investigation live | `AgentTrace` (subject), `AgentEventListener` (`AuditLog`, `CaseView`, the CLI) |
| Adapter | One `LLMClient` interface over different providers | `LangChain4jClient` with `AnthropicLLMClient` and `OpenAiCompatibleLLMClient` |
| Decorator | Retry, recording and fault injection around a client or a tool, and data cut at the alert | `LLMClientDecorator`: `RetryingLLMClient`, `RecordingLLMClient`; `FaultInjectingTool` over `AgentTool`; `AsOfLedger` over `Ledger` |
| Facade | Both front ends use one entry point | `TraceWiseFacade`, built by `TraceWiseBootstrap` |

TODO (period 3): the updated UMLet class diagram (4 figures, as in Stage 1), adding `AsOfLedger`,
`InvestigationCutoff`, `SamlDCsvFormat`, `SchemeSampler`, the `metrics` package and `FinaliseReportCommand`; and
the redrawn sequence diagrams SD7 and SD9 (design change 49).

# 4. Changes and rationale

No Stage 1 feature or design pattern was removed, replaced or added. The architecture (three modules over one
facade), the 12 features and the 9 patterns are as designed. Five changes alter what the design described:

| Original design | Implemented change | Reason | Impact |
|---|---|---|---|
| F11: finalising an STR is an approved escalation (`EscalateCaseCommand` routed to the approval queue, SD7) | Finalisation is its own command, `FinaliseReportCommand`, allowed only once the case is escalated and always sent to the approval queue; it carries the text it was requested for, so a later edit expires it | Escalating a case and filing its report are two decisions; tying the request to its text stops an edited report from being filed on an old approval | F11, UC12; `FinaliseReportCommand`, `PermissionPolicy`, `ReportService`; Command pattern unchanged (one more command); SD7 redrawn |
| F03: the agent's read tools see every transaction in the data | The tools see the data as it stood at the alert's last flagged transaction (`AsOfLedger` decorating `Ledger`, the cutoff in `InvestigationCutoff`); the assistant still sees everything | Without it the agent judged accounts with months of hindsight a bank would not have (one account had 122 of 149 transactions after its alert) | F03, F07; read tools, `ActionScope`; a new participant in the existing Decorator pattern |
| F03: the agent escalates on "reasonable grounds to suspect" and dismisses on "a clear legitimate explanation" | The agent first describes the account's usual activity, then asks whether the flagged activity is new for it and where the money came from and went | With the original wording the agent escalated 75 of 75 alerts, because the data never contains a customer's explanation | F03 instructions only (`InvestigationTask`); see the measured results in section 1 |
| Data: IBM's synthetic data and the scenario generator | A third source, SAML-D (a UK bank's 9.5 million synthetic transactions with normal and suspicious typologies), read by a new `SamlDCsvFormat` and used to measure the agent | IBM's normal accounts behave suspiciously, so IBM cannot test whether the agent clears a false alarm | F01 (one format class behind the existing Strategy), F12 evaluation data |
| F12: an alert is laundering if any of its transactions is labelled; escalating any other alert is a false positive | A false alarm is a clean alert on an account with no laundering anywhere in the data; a clean alert on an account that launders elsewhere is counted apart | 10 of the 12 "false positives" in the first measurement were on laundering accounts the agent was told to look into | F12 metrics (`AlertLabel`, `PerformanceService`, `PerformanceMetrics`) |

The other differences are details inside the design, grouped below. Each is listed with its reason in
[`docs/stage2/design-changes.md`](design-changes.md).

| Group | What it covers |
|---|---|
| Interface details | Extra parameters and fields on the designed classes, and small helper types they need (for example `RiskScorer.score` also takes the rules triggered, `TraceStep` names its subject, `TotalClaim` for verified totals, `AssistantContext(caseId, accountId)`). Same classes, roles and patterns |
| Behaviour the design left open | Values and rules Stage 1 did not specify: which LLM errors are retried, at most 10 tool calls per model response, triage defaults (50 alerts, CAD 5.00, stop after 3 failures in a row), the agent acting only on its own case, when waiting proposals expire, how replays match, when the indicator index is built, import batching |
| Data format and presentation | How the data files mark cash and outside parties; the FINTRAC indicator list used; the GUI's look, keyboard shortcuts and command bar (docs/stage2/ui-design.md); SD7 and SD9 redrawn for the first change above |

# 5. AI tools and collaboration

[`docs/ai-usage/ai-tools.md`](../ai-usage/ai-tools.md) lists every tool and model with its purpose, the token
use of development per period, and the cost of TraceWise's own model calls.

| Item | Amount |
|---|---|
| Development (Claude Code, Opus 5.5 with Sonnet 5.5 subagents), Stage 2 to 2026-10-29 | 3,656 model calls, 2.6M output tokens, 877M cache-read tokens; about USD 484 API-equivalent, covered by a Claude Max subscription |
| TraceWise's own calls (Anthropic API) | about CAD 64, mostly the accuracy evaluations (CAD 19.5 for the Sonnet comparison) |

[`docs/ai-usage/collaboration-log.md`](../ai-usage/collaboration-log.md) has 16 entries with the task, the
instruction, the AI's contribution, the human's contribution and the outcome. The pattern throughout: Kian set
direction and limits (Java and LangChain4j, stay within the Stage 1 design, Haiku first and Sonnet last, no made-up
data, no other AI models in reviews, approve spend), and Claude Code implemented test-first and reviewed every
feature with a multi-reviewer review whose findings were each checked by an independent validator.

TODO (period 3): final token figures for period 3.

# 6. Development and verification summary

**Method.** Test-driven development (each fix starts from a failing test), one `deep-code-review` per feature or
batch (eleven so far, each finding validated independently before it was fixed), live checks against the
Anthropic API with recordings committed so tests and graders can replay the agent without a key, and an x86
Docker run of the test suite.

| Period | Commits (code / all) | Summary | Links |
|---|---|---|---|
| 1: Oct 20 to 26 | 44 / 59 | Build, F01 and F02 with CLI, LLM layer (Adapter, retry and replay Decorators), agent loop and read tools, claim verifier, cases (State), F03, F05, F06 (Command, Factory Method), F07; three reviews | [commits](https://github.com/kiankhooban/TraceWise/commits/main?since=2026-10-20&until=2026-10-26) |
| 2: Oct 27 to Nov 2 (to Oct 29) | 48 / 67 | F04, F10, the JavaFX GUI, F08, F11, F09, F12; real data (IBM, SAML-D) and the accuracy work; design audit, three modules, missing pattern classes; reviews 4 to 11 | [commits](https://github.com/kiankhooban/TraceWise/commits/main?since=2026-10-27&until=2026-11-02) |
| 3: Nov 3 to 9 | TODO | UI polish, F10 GUI details, diagrams, final review, fresh-clone check | TODO |

**Verification.** 407 tests: unit tests per class, service tests on a real SQLite database, CLI tests, replayed
agent runs (`CommittedReplaysTest`), and tests for each review finding. TODO (period 3): fresh-clone check
following the README, and a secret scan of the history.

# 7. Demonstration index

TODO (Nov 7 to 10): one video segment per feature, together covering the GUI and the CLI.

| Feature | Video segment | Shows |
|---|---|---|
| F01 to F12 | TODO | TODO |
