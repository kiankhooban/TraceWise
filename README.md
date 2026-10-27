# TraceWise

An AI agent that investigates anti-money-laundering (AML) alerts.

A deterministic rules engine flags suspicious transactions using thresholds modelled on FINTRAC
reporting rules. An LLM agent then investigates each alert: it plans its steps, calls tools over the
transaction ledger, retrieves matching FINTRAC money-laundering indicators, and drafts a case summary.
A deterministic verifier checks every amount, date and transaction ID the agent cites against the
ledger before a human analyst approves or dismisses the case.

> Course project for EECS3311 Software Design (York University, Fall 2026).
> All data is synthetic. TraceWise is a learning project, not compliance software or legal advice.

## Status

| Stage | Content | Status |
|---|---|---|
| Stage 1: Design | [Design report](docs/stage1/Stage1-Design-Report.md) ([PDF](docs/stage1/Stage1-Design-Report.pdf)) (UMLet diagrams in [`docs/stage1/umlet`](docs/stage1/umlet)) | Complete (100/100) |
| Stage 2: Implementation | [Plan and feature status](docs/stage2/PLAN.md), [design changes](docs/stage2/design-changes.md) | In progress: F01 to F07 working from the CLI |
| Stage 3: Testing | JUnit tests, KUMA agent behaviour tests | Not started |

## Quick start

Prerequisites: Java 21 (JDK). Maven is not needed: the repository includes the Maven Wrapper.

```bash
./mvnw package                                  # builds target/tracewise.jar and runs the tests
./tracewise import data/samples/scenarios.csv   # load the sample transactions
./tracewise monitor run                         # run the six monitoring rules
./tracewise alerts list                         # the alert queue, highest risk first
./tracewise threshold-reports list              # the Threshold Report Register
./tracewise rules list
./tracewise --role supervisor rules set R3 --set window_days=10
./tracewise investigate ALR-00001               # the agent investigates an alert (needs an API key)
./tracewise triage --max-alerts 50 --max-cost 5.00  # the agent works through the open alerts, highest risk first
./tracewise approvals list                      # closes and escalations the agent proposed
./tracewise approvals approve APR-00001         # or: approvals reject APR-00001 --reason "..."
./tracewise indicators search deposits below the reporting threshold   # FINTRAC indicators and past cases
./tracewise cases similar CAS-00003             # decided cases with the same indicators
```

Indicator search runs a local embedding model (bge-small-en-v1.5) inside the JVM: no key, no network,
about 2 seconds the first time a command needs it.

The agent sets priorities, links alerts, watchlists accounts and creates tasks on its own; it can only
propose closing or escalating a case, which an analyst approves or rejects.

Every command accepts `--json` for machine-readable output. The database is `tracewise.db` in the
current directory unless `--db FILE` or `TRACEWISE_DB` says otherwise.

### Configuration and API key

Features that call the LLM need an Anthropic API key. Keep it out of git:

```bash
cp .env.example .env      # .env is gitignored
# then edit .env and replace the placeholder with your own key
```

Never commit `.env` or paste a key into code, reports, screenshots or videos.

Model options (each also has an environment variable):

```bash
./tracewise --model claude-haiku-5-5 investigate ALR-00001       # cheaper model (default: claude-sonnet-5-5)
./tracewise --record investigate ALR-00001                       # also save the answers to data/replays
./tracewise --llm replay investigate ALR-00001                   # replay saved answers: no key, no cost
```

`--llm replay` answers only requests that were recorded, so it reproduces a recorded run on the
same data. `data/replays` holds recorded investigations of the four sample alerts (Claude Haiku 5.5,
with the agent's actions and proposals),
so on a fresh database built from `data/samples/scenarios.csv` they replay without a key:

```bash
./tracewise import data/samples/scenarios.csv && ./tracewise monitor run
./tracewise --llm replay --model claude-haiku-5-5 investigate ALR-00001
./tracewise --llm replay --model claude-haiku-5-5 triage      # all four, in risk order
```

Replays reproduce the recorded requests only. Approving or rejecting a proposal changes what later
runs see (past decisions and similar cases), so after a decision further investigations need a live
model; the replay error says so.

Each investigation prints its token usage and cost in CAD.

### Sample data

`data/samples/scenarios.csv` is synthetic, generated with a fixed seed by
`tracewise.tools.ScenarioGenerator`. It plants one scheme per monitoring rule, boundary cases
that must not fire, and 60 normal customers that trigger nothing:

```bash
java -cp target/tracewise.jar tracewise.tools.ScenarioGenerator 42 data/samples/scenarios.csv
```

## Stack

Java 21, JavaFX (GUI), picocli (CLI), LangChain4j (LLM access), Anthropic Claude models,
SQLite, JUnit, Maven.

## Repository layout

```
src/main/java/tracewise/   application code (ui, app, ingest, monitoring, agent, llm, verification, cases,
                           domain, persistence, audit, tools)
src/test/java/tracewise/   JUnit tests
data/samples/              synthetic sample data
docs/
  stage1/                  Stage 1 design report; UML in UMLet (umlet/), earlier PlantUML drafts (diagrams/)
  stage2/                  Stage 2 plan, feature status, design-change log
  ai-usage/                AI tools used and the AI-human collaboration log
```
