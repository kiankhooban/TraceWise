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

## Feature status
| Feature | Status | Implemented in | Video |
|---|---|---|---|
| F01 Transaction Data Import | Working (CLI); GUI pending | `ingest/*`, `app/ImportService`, `persistence/SqliteTransactionRepository`, `ui/cli/ImportCli` | |
| F02 Rule-Based Monitoring, Threshold Reporting and Alert Queue | Working (CLI); GUI pending | `monitoring/*` (six rules, `WindowedRule`, `RiskScorer`), `app/MonitoringService`, alert, register and rule-config repositories, `ui/cli/{Monitor,Alerts,ThresholdReports,Rules}Cli` | |
| F03 Autonomous Alert Investigation | Working (CLI); GUI pending; retrieval tools come with F04 | `agent/*` (`AgentRunner`, `AgentTrace`, `InvestigationTask`), `agent/tools/*` (`ToolRegistry`, read tools), `llm/*` (retry, record, replay decorators, factory), `app/InvestigationService`, `ui/cli/InvestigateCli` | |
| F04 Indicator Retrieval and Case Memory | Not started | | |
| F05 Claim Verification | Working (CLI, inside `investigate`) | `verification/*` (`ClaimVerifier`, `VerificationReport`), `agent/Claim`, `agent/TotalClaim` | |
| F06 Agent Actions and Approval Queue | Working (CLI); GUI pending; report-draft action comes with F11 | `actions/*` (`ActionCommand` and six commands, `CommandDispatcher`, `PermissionPolicy`, `ApprovalQueue`, `PendingAction`), `agent/tools/ActionTool` and six action tools, `persistence/SqliteApprovalRepository`, `ui/cli/ApprovalsCli` | |
| F07 Autonomous Queue Triage | Not started | | |
| F08 Case Management and Audit Trail | Partly: case states (State pattern) and storage, agent steps audited; case and audit commands pending | `domain/Case`, `cases/state/*`, `persistence/SqliteCaseRepository`, `audit/AuditLog` | |
| F09 Money-Flow Network View | Not started | | |
| F10 Natural-Language Analyst Assistant | Not started | | |
| F11 Suspicious Transaction Report Drafting and Export | Not started | | |
| F12 Agent Performance Dashboard | Not started | | |

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

## Tooling (installed 2026-10-20)
- `jdtls-lsp` plugin with `jdtls` 1.63.0 (Homebrew): Java diagnostics and navigation.
- `ponytail` 5.3.2: level `full`; cost ledger on, webhook off, task names not sent.
- `session-report`: token and cost reports from local transcripts, for the AI tools table.
- Already present: superpowers, context7, `/code-review`, `deep-code-review`.
- Considered and not installed: pstack (overlaps superpowers and ponytail, re-opens the graded design
  through per-feature design exploration, depends on Cursor-only features).
