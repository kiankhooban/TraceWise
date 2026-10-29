# TraceWise: operating notes for Claude Code

TraceWise is Kian Khooban's EECS3311 (York, Fall 2026) course project: a Java AI agent that
investigates AML alerts. Stage 1 (design) is done and graded 100/100, tagged `stage1-submitted`.
Stage 2 (implementation) runs 2026-10-20 to 2026-11-10. Plan and status: `docs/stage2/PLAN.md`.
Resuming work: read `docs/stage2/HANDOFF.md` first (next task, code conventions, pitfalls).

## The design is the spec
- `docs/stage1/Stage1-Design-Report.md` defines the 12 features (F01-F12), use cases, classes,
  methods and the 9 design patterns. Build what it says. Do not redesign it.
- Every deviation (feature, class, method, pattern) is recorded in `docs/stage2/design-changes.md`
  when it happens, as Original design -> Implemented change -> Reason -> Impact. Stage 2 rejects
  unexplained differences.
- Patterns must be evident in the code and solve the problem the report names; a class named after
  a pattern is not enough.

## How we code
- ponytail at level `full`, never `ultra`. The Stage 1 design counts as "what the user asked for",
  so its interfaces and pattern structure (Strategy, Template Method, Factory Method, Command, State,
  Observer, Adapter, Decorator, Facade) are never cut as "unrequested abstraction".
- superpowers: `test-driven-development` for application code, `systematic-debugging` for bugs,
  `verification-before-completion` before claiming anything works.
- context7 for LangChain4j, JavaFX, picocli and sqlite-jdbc APIs. LangChain4j changes often.
- Reviews: built-in `/code-review` or `deep-code-review` only.
- Modules (Stage 1 architecture): `core` (tracewise-core, all logic, no UI), `cli` and `gui` (front ends over
  `TraceWiseFacade` only). Stack: Java 21, Maven Wrapper (`./mvnw`, no system Maven), JavaFX 21, picocli 4.7.7,
  sqlite-jdbc 3.53.4.0 (WAL), LangChain4j 1.21.0, bge-small-en-v1.5 quantized embeddings.
- Features must really work: no mockups, empty methods or hard-coded demo responses.

## Commits
- At least 5 meaningful code commits pushed in each period (Toronto time): Oct 20-26,
  Oct 27-Nov 2, Nov 3-9. Report-only, formatting-only or artificially split commits do not count.
- Commit and push as real units of progress land. Descriptive messages.
- End every commit message with the `Claude-Session:` trailer given by the session.

## Secrets
- The LLM key lives only in `.env` in the repo root (gitignored). `.env.example` holds placeholders.
- Never print, log, commit or paste a key. Kian enters it himself.
- Never `source` or `.` the `.env` file: a malformed line gets executed and the shell prints it,
  key included (this happened on 2026-10-20 and the key had to be revoked). Read `.env` as text,
  take only `ANTHROPIC_API_KEY`, and report problems by line number, never by content.

## Records the Stage 2 report needs (keep them current)
- `docs/ai-usage/collaboration-log.md`: add an entry for each representative interaction.
- `docs/ai-usage/ai-tools.md`: tools, models, and token and money cost. Cost sources: ponytail's
  ledger `~/.config/ponytail/band/`, and the `session-report` plugin. Kian is on Claude Max, so
  development cost is reported as tokens plus the subscription for the Stage 2 period (any
  API-equivalent dollar figure is labelled an estimate). The app's own LLM calls use Kian's API key
  and are reported separately, from the Anthropic Console and the app's trace token counts.
- `docs/stage2/design-changes.md` and the feature-to-implementation mapping in `docs/stage2/PLAN.md`.

## Style
- No em dashes anywhere (code comments, docs, commit messages). Use colons or restructure.
- UML is drawn in UMLet: generators in `docs/stage1/umlet/`, `./render.sh` exports with UMLet.
- Discuss before large or outward-facing actions. Kian wants to agree on direction first.
