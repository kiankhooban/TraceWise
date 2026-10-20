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

## Schedule
| Period | Code | Records |
|---|---|---|
| 1: Oct 20-26 | Maven skeleton, `.env.example`, domain model, SQLite repositories, F01 import with sample data, F02 rules and alert queue, picocli skeleton, tests | design-changes log started |
| 2: Oct 27-Nov 2 | LLMClient (LangChain4j adapter, retry, replay), agent loop and tools (F03), retrieval (F04), verifier (F05), commands and approvals (F06), triage (F07), CLI `ask --json` (F10), JavaFX shell | collaboration entries |
| 3: Nov 3-9 | JavaFX views, case states and audit (F08), network view (F09), STR draft and export (F11), dashboard (F12), fixes | updated UMLet class diagram, report draft, demo scripts |
| Nov 10 | Fresh-clone README check, secret scan of history | Kian records videos; report PDF and demo index |

## Feature status
| Feature | Status | Implemented in | Video |
|---|---|---|---|
| F01 Transaction Data Import | Not started | | |
| F02 Rule-Based Monitoring, Threshold Reporting and Alert Queue | Not started | | |
| F03 Autonomous Alert Investigation | Not started | | |
| F04 Indicator Retrieval and Case Memory | Not started | | |
| F05 Claim Verification | Not started | | |
| F06 Agent Actions and Approval Queue | Not started | | |
| F07 Autonomous Queue Triage | Not started | | |
| F08 Case Management and Audit Trail | Not started | | |
| F09 Money-Flow Network View | Not started | | |
| F10 Natural-Language Analyst Assistant | Not started | | |
| F11 Suspicious Transaction Report Drafting and Export | Not started | | |
| F12 Agent Performance Dashboard | Not started | | |

## Tooling (installed 2026-10-20)
- `jdtls-lsp` plugin with `jdtls` 1.63.0 (Homebrew): Java diagnostics and navigation.
- `ponytail` 5.3.2: level `full`; cost ledger on, webhook off, task names not sent.
- `session-report`: token and cost reports from local transcripts, for the AI tools table.
- Already present: superpowers, context7, `/code-review`, `deep-code-review`.
- Considered and not installed: pstack (overlaps superpowers and ponytail, re-opens the graded design
  through per-feature design exploration, depends on Cursor-only features).
