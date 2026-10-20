# Stage 2 handoff (for the next Claude session or after /compact)

Last updated 2026-10-20 after F07 and its review. Read this, then `CLAUDE.md` (repo root) and
`docs/stage2/PLAN.md`. The session that wrote this ran from `~/Brain`, so the TraceWise `CLAUDE.md`
is NOT loaded automatically there: read it explicitly.

## Where we are
- Stage 1 graded 100/100, tagged `stage1-submitted`. Stage 2 due 2026-11-10; feature freeze Nov 7.
- Done and pushed (period 1, Oct 20): build skeleton, F01 import, F02 monitoring, CLI, sample data,
  audit log, `EnvFile`; LLM layer (`LLMClient`, Anthropic adapter, `RetryingLLMClient`,
  `RecordingLLMClient`, `ReplayLLMClient`, `LLMClientFactory`); `ToolRegistry` with validation,
  `account_history` and `counterparties` tools; `AgentRunner`, `AgentTrace` (Observer, `AuditLog`
  listens); `ClaimVerifier` (F05); `Case` with the State pattern and `SqliteCaseRepository`;
  `InvestigationService` and `tracewise investigate <alertId> [--json]` (F03).
- Live check (Haiku 5.5, sample data): all 4 alerts COMPLETED, ESCALATE, every claim confirmed, CAD 0.012.
- 181 tests pass, 1 skipped (the opt-in live test). Design changes 1 to 20 logged.
- Patterns in code: Facade, Strategy, Template Method, Adapter, Decorator, Factory Method (client
  factory), Observer, State, Command (F06). All nine designed patterns are now in code.
- F06 done: actions with permission tiers, approval queue, `approvals` CLI, agent actions scoped to its case.
- F07 done: `TriageService`, `tracewise triage`; period 1 plan complete.

## Next task, in this order (period 2 work, started early)
1. F04: retrieval of FINTRAC indicators and similar cases (bge-small-en-v1.5 quantized embeddings,
   `Retriever`, `SearchIndicatorsTool`, `SimilarCasesTool`, case memory).
2. F10 `ask --json`, then the JavaFX shell with Alerts, Case and Approvals screens, F08 case commands
   (`case show`, notes, reopen, human close and escalate through the dispatcher).

Design sources for these: `docs/stage1/diagrams/src/model.iuml` (full 146-classifier model with
every member; grep the class name), report sections F03 to F07 and Task 4 in
`docs/stage1/Stage1-Design-Report.md`, sequence diagrams SD3 (investigate), SD4 (triage),
SD5 (action and approval) in `docs/stage1/umlet/gen_sequence_diagrams.py`.

## Conventions established in the code
- Packages: `tracewise.{domain, ingest, monitoring, persistence, audit, app, llm, ui.cli, tools}`.
  Agent classes go in `tracewise.agent`, actions in `tracewise.actions`.
- TDD: write the test, run `./mvnw -q test 2>&1 | grep -c "cannot find symbol"` (or check the
  failure) to see RED, implement, `./mvnw test 2>&1 | grep -E "Tests run:|BUILD" | tail -2`.
- Persistence: repositories take `Database`; use the `Jdbc.query/update` helpers; schema is
  `src/main/resources/tracewise/schema.sql` (statements split on `;` + newline before the next
  CREATE, so trigger bodies survive). `db.inTransaction(...)` nests.
- Tests: `@TempDir` real SQLite; `monitoring/InMemoryLedger` test double; `ScenarioGenerator.generate(42)`
  for integration data (expected monitoring result: 4 threshold reports, alerts R3, R4, R5, R6 on
  the roles S-C1, P-C1, F-C1, O-C1). Ids are neutral (C1037, TX-00001); `ScenarioGenerator.accounts(42)`
  maps role names to ids. Never put scheme names in ids the agent can read.
- New user operations go through `TraceWiseFacade` and are wired in `TraceWiseBootstrap`.
  CLI commands extend `CliCommandBase` (override `execute()`, optionally `print()` for text;
  `--json` is automatic); register them in `TraceWiseCli` subcommands.
- Session identity: `--role analyst|supervisor` or `TRACEWISE_ROLE`; `Actor` in audit entries.
- Parties outside the bank: account id `CASH` or prefix `EXT-` (not stored, not monitored).
- Every deviation from Stage 1 goes in `docs/stage2/design-changes.md` immediately.
- Commit after each working unit, push, end the message with the session's `Claude-Session:` trailer.

## Commands and pitfalls
- Build jar: `./mvnw -q package -DskipTests`; run: `./tracewise ...`.
- Manual runs: set `export TRACEWISE_DB=/path/x.db` (zsh does not word-split `$VAR` into `--db x`).
- zsh: `rm -f a.db*` fails when nothing matches; name files explicitly.
- Live LLM test (costs ~$0.0001): `TRACEWISE_LIVE_LLM=1 ./mvnw test -Dtest=AnthropicLiveTest`.
- NEVER `source` `.env`. Read it with `EnvFile` or a Python text parse; never print the key.
- Models: dev checks `claude-haiku-5-5` ($0.10 in / $0.50 out per MTok, prompts under 100k);
  real runs and demos `claude-sonnet-5-5` ($2 / $10). Verified via the API models list 2026-10-20.
- LangChain4j stays on 1.21.0 (the graded design's version) although 1.22.0 exists.
- Reading money from JSON: use `USE_BIG_DECIMAL_FOR_FLOATS` and turn off
  `STRIP_TRAILING_BIGDECIMAL_ZEROES` (see `InvestigationTask`, `SqliteCaseRepository`).
- Replay matches whole requests: prompts must never include the current time or random values.
- Live runs: `./tracewise --model claude-haiku-5-5 --record --replays <scratch dir> investigate ALR-00001`
  (CAD 0.003 per alert). `data/replays` holds the committed recordings of the four sample alerts.
- Tests reuse `monitoring/InMemoryLedger` and `agent/AgentRunnerTest.ScriptedModel` (both public).

## Open items to remember
- JavaFX GUI not started (weeks 2-3). F01, F02, F03, F05 have CLI only.
- `AgentLimits.DEFAULT` is 12 turns and CAD 1.00 per investigation (no value in the design).
- From the 2026-10-20 review, not fixed yet:
  - One investigation at a time per `InvestigationService` (shared runner; findings appended by count).
    Before the GUI runs investigations on background threads, refuse a second concurrent run or give
    each run its own runner, and append findings with a plain INSERT.
  - The agent sees only flagged transaction ids; on a busy account they can fall outside the 100 rows
    `account_history` lists. Give it the flagged rows (or a lookup tool) and the rule's live
    thresholds; the design's other read tools (linked alerts, window aggregates) are still to build.
  - Stored findings have no format version; renaming a field makes old rows unreadable.
  - Re-record `data/replays` after any change to the prompt, tools or sample data (replay matches
    whole requests). Keep tool results free of values that depend on run order (global ids, times).
- From the F06 review, not fixed yet (all need concurrency the CLI does not have; handle with the GUI):
  two runs linking the same alert, one run's cancel reaching another, a cancel that lands just after a
  run ends. Also: `approvals` ids come from COUNT(*)+1 (fine while rows are never deleted); the reviewer's identity is
  the `--role` the session claims (the GUI must not trust a client-side role).
- `OpenAiCompatibleLLMClient` from the design is not built; build it or log it as a design change.
- Kian revoked the key exposed on 2026-10-20 (Console showed 0 keys); the new key is in `.env`.
- Kian records the demo videos himself; we prepare a script per feature in week 3.
