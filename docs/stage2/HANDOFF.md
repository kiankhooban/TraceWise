# Stage 2 handoff (for the next Claude session or after /compact)

Last updated 2026-10-20 after commit `ff5ce65`. Read this, then `CLAUDE.md` (repo root) and
`docs/stage2/PLAN.md`. The session that wrote this ran from `~/Brain`, so the TraceWise `CLAUDE.md`
is NOT loaded automatically there: read it explicitly.

## Where we are
- Stage 1 graded 100/100, tagged `stage1-submitted`. Stage 2 due 2026-11-10; feature freeze Nov 7.
- Done and pushed (period 1, Oct 20): build skeleton, F01 import, F02 monitoring (six rules, risk
  scoring, alerts, Threshold Report Register, persisted rule configs, supervisor-only changes),
  CLI for both, scenario generator + `data/samples/scenarios.csv` (seed 42), audit log
  (append-only), `EnvFile`, `LLMClient` + LangChain4j adapter + `AnthropicLLMClient`.
- 74 tests pass, 1 skipped (the opt-in live test). Design changes 1 to 6 logged.
- Patterns in code so far: Facade, Strategy (rules, CSV formats), Template Method (`WindowedRule`,
  `CliCommandBase`), Adapter (`LangChain4jClient`).

## Next task, in this order (period 1, front-loaded)
1. `LLMClientDecorator` (abstract, holds `inner`) + `RetryingLLMClient` (maxRetries 2): Decorator.
2. `ReplayLLMClient` / `RecordingLLMClient` + `ReplayStore` so agent tests and demos run without
   spending; add `record` to `ModelConfig` then. `LLMClientFactory.create(config)` stacks them.
3. Agent core: `AgentTrace` (subject: `addListener`, `record(step)`, `finish(outcome)`,
   `examinedAccounts()`), `AgentEventListener` (`onStep`, `onFinished`), `TraceStep`, `AgentOutcome`
   (COMPLETED / INCOMPLETE / FAILED / CANCELLED), `AgentLimits` (12 turns, cost limit),
   `AgentRunner.run(task, trace)` with `cancel()`. Our own loop, NOT LangChain4j `AiServices`
   (Stage 1 key design decision 1). `AuditLog` becomes an `AgentEventListener`: Observer.
4. `ToolRegistry` (`register`, `schemas(names)`, `execute(call)`; validates arguments before running
   a tool), `AgentTool` interface, read tools `AccountHistoryTool` and `CounterpartiesTool` on `Ledger`.
5. `ClaimVerifier` (F05): checks each cited transaction via `Ledger.findTransaction`, amount within
   $0.01, date and account; `VerificationReport`, `Claim`, `ClaimCheck`; `AgentFinding.claims()`,
   `attachVerification()`.
6. `InvestigationService.investigate(alertId, listener)` + `cancel`, `InvestigationTask` (prompt,
   tools, finding parsing), `Case` with `CaseState` (State; at least Open, UnderInvestigation),
   `CaseRepository`. CLI `tracewise investigate ALR-00001 [--json]`. This is F03.
7. Then F06 (commands, `PermissionPolicy`, `CommandDispatcher`, `ApprovalQueue`, `PendingAction`,
   action tools with `createCommand()`: Command, Factory Method, State) and F07 (`TriageService`).

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
  for integration data (expected monitoring result: 4 threshold reports, alerts R3 S-C1, R4 P-C1,
  R5 F-C1, R6 O-C1).
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

## Open items to remember
- JavaFX GUI not started (weeks 2-3). F01/F02 have CLI only.
- `OpenAiCompatibleLLMClient` from the design is not built; build it or log it as a design change.
- Kian revoked the key exposed on 2026-10-20 (Console showed 0 keys); the new key is in `.env`.
- Kian records the demo videos himself; we prepare a script per feature in week 3.
