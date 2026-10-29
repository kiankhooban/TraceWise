# AI Tools and Models Used

Required by the project instructions, Stage 2, section 9. Updated throughout the project.

| Tool | LLM model | Purpose | Example tasks |
|---|---|---|---|
| Claude Code (CLI) | Claude Opus 5.5 (`claude-opus-5-5`) | Requirements analysis, domain research, design drafting, UML generation | Read the course specification, researched Canadian bank AI work and FINTRAC rules, drafted the Stage 1 design report and generated its UML diagrams (PlantUML drafts, then the submitted UMLet diagrams) |
| Claude Code subagents | Inherited from the parent session (Opus 5.5) unless set to Sonnet 5.5 | Research, diagnosis and audits run in parallel | Bank hiring signals, AML regulation and dataset research; three agents diagnosing why every IBM alert was escalated; two audits of the code against the Stage 1 design (2026-10-29) |
| Claude Code (CLI), Stage 2 | Claude Opus 5.5 (`claude-opus-5-5`) | Implementation, test-first, with the skills below | Wrote the F01 to F12 code and tests, the three-module build, the evaluation scripts, the design-change log and these records; ran the live checks, the accuracy evaluations and two design audits against the Stage 1 report |
| Claude Code skills and plugins | n/a (instructions loaded into the session) | Working method | superpowers (test-driven development, debugging, verification), ponytail (smallest complete change), context7 (current library docs, e.g. LangChain4j error types), jdtls (Java language server), session-report (token reports) |
| `deep-code-review` skill | Claude Opus 5.5 for correctness, security and adversarial reviewers; Claude Sonnet 5.5 for the others and for the per-finding validators | Multi-reviewer code review before each feature was accepted | Eleven reviews (2026-10-20 to 2026-10-29), one per feature or batch of fixes; every finding checked by an independent validator before it was fixed |
| OpenAI Codex CLI | Codex default model (not exposed by the CLI) | Independent second-model adversarial review inside `deep-code-review` | Found the two-runs and budget-cap issues; agreed with Claude reviewers on the triage queue problem. Usage limit reached on 2026-10-27 (until 2026-11-19), so later reviews had no Codex pass |
| Gemini through Antigravity CLI (`agy`) | Gemini, top model of Kian's plan | Tried as the second-model pass in review 10 (2026-10-29) | Returned no review: a network reset, then "Individual quota reached". Kian then asked that reviews use no models other than Claude |

## Cost

**Development (Claude Code):** covered by Kian's Claude Max subscription; no money was spent per token.
Token counts are summed from Claude Code's local session logs for the TraceWise session and its subagents
(the same logs the `session-report` plugin reads), by day in Toronto time. The dollar column is an
API-equivalent estimate only: Opus 5.5 USD 4 / 20 and Sonnet 5.5 USD 2 / 10 per million input / output
tokens (Stage 1 price table), cache writes at 1.25 times and cache reads at 0.1 times the input price.

| Period | Model calls (Opus / Sonnet) | Input (uncached) | Cache write | Cache read | Output | API-equivalent |
|---|---|---|---|---|---|---|
| Stage 1, 2026-09-29 to 10-06 | 384 / 68 | 928 | 2.56M | 142.9M | 0.52M | about USD 78 |
| Stage 2 period 1, 10-20 to 10-26 | 519 / 300 | 1,754 | 7.44M | 201.1M | 0.54M | about USD 116 |
| Stage 2 period 2, 10-27 to 10-29 (to date) | 1,931 / 903 | 5,922 | 19.51M | 675.8M | 2.10M | about USD 368 |

Codex and Gemini ran under their own subscriptions and are not in these counts.

**TraceWise's own model calls (Anthropic API, Kian's key):**

| Date | Runs | Model | Cost (CAD) |
|---|---|---|---|
| 2026-10-20 | API check (1 call) and 21 live investigations: first checks, two re-recordings of the four sample alerts, the F06 check | Claude Haiku 5.5 | about 0.06 |
| 2026-10-27 | 4 live investigations: re-recording the four sample alerts with the F04 retrieval tools | Claude Haiku 5.5 | about 0.025 |
| 2026-10-27 | 5 live assistant questions (F10), recorded for replay | Claude Haiku 5.5 | about 0.004 |
| 2026-10-27 | 14 live assistant questions: two re-recordings of 7 questions after the F10 review fixes | Claude Haiku 5.5 | about 0.022 |
| 2026-10-27 | 7 live assistant questions, re-recorded after `get_case` gained notes | Claude Haiku 5.5 | about 0.011 |
| 2026-10-27 | 6 live report narratives (F11): the three escalated sample cases, recorded twice (before and after the review fixes) | Claude Haiku 5.5 | about 0.005 |
| 2026-10-28 | 2 live investigations of a busy account in IBM's HI-Small data (before and after `window_aggregates`) | Claude Haiku 5.5 | about 0.19 |
| 2026-10-28 | Two full re-recordings of the replays (4 alerts, 7 questions, 3 report drafts each) after the tool set and a tool description changed | Claude Haiku 5.5 | about 0.08 |
| 2026-10-28 | F12 live check: triage of 8 alerts from IBM sample part 1 | Claude Haiku 5.5 | about 0.17 |
| 2026-10-28 | Accuracy work on IBM data: Haiku baseline and a second instruction set (66 runs), three versions on a fixed 75-alert set (225 runs) | Claude Haiku 5.5 | about 5.8 |
| 2026-10-28 | Capability check on IBM data: 20 alerts | Claude Sonnet 5.5 | about 4.1 |
| 2026-10-28 | Accuracy work on SAML-D: four instruction versions on a 75-alert tuning set (300 runs), v5 on a 75-alert held-out set, replay re-recordings | Claude Haiku 5.5 | about 16.5 |
| 2026-10-28 | Model comparison started on the SAML-D held-out set before the review 10 fixes, stopped after 39 alerts (does not count) | Claude Sonnet 5.5 | about 10 |
| 2026-10-29 | v6 instructions with the data cut at the alert: 75-alert tuning set and 75-alert held-out set (150 runs with a finding, plus runs lost to a network outage that cost nothing) | Claude Haiku 5.5 | about 3.7 |
| 2026-10-29 | The same two sets again after `linked_alerts` stopped showing later alerts (150 runs), and the re-recorded replays | Claude Haiku 5.5 | about 3.6 |
| 2026-10-29 | Model comparison (F12): v6 on the 75-alert held-out set | Claude Sonnet 5.5 | about 19.5 |

The cost per run is computed by TraceWise from token counts and the published prices (USD 0.10 in /
0.50 out per million tokens for Haiku 5.5, converted at the Bank of Canada rate); the Anthropic Console
is the authoritative figure. Replays of the committed recordings cost nothing.
