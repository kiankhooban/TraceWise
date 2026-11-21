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
| Stage 2: Implementation | [Implementation report](docs/stage2/Stage2-Report.md) ([PDF](docs/stage2/stage2-report.pdf)), [design changes](docs/stage2/design-changes.md), [UML](docs/stage2/umlet) | All 12 features working from the CLI and the GUI |
| Stage 3: Testing | JUnit tests, KUMA agent behaviour tests | Not started |

## Quick start

Prerequisites: Java 21 (JDK). Maven is not needed: the repository includes the Maven Wrapper.

```bash
./mvnw package                                  # builds the CLI and GUI jars and runs the tests
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
./tracewise ask "Why is account C1037 risky?"   # the read-only assistant (add --json for scripts)
./tracewise case show CAS-00001                 # also: case note, case close --reason, case reopen --reason
./tracewise audit export --from 2026-10-01 --out audit.csv
./tracewise report draft CAS-00001              # the agent drafts the STR narrative (also: report show, report edit)
./tracewise report finalise CAS-00001           # waits in the approval queue as a proposal (APR-...)
./tracewise approvals approve APR-00004         # the id printed by report finalise; then the report can be exported
./tracewise report export CAS-00001 --format both --out reports   # HTML and JSON; nothing is sent to FINTRAC
./tracewise network C1037 --hops 2              # who the account sends money to and receives it from
./tracewise metrics                             # the agent's precision, recall, cost and failure rate (also --csv)
```

Indicator search runs a local embedding model (bge-small-en-v1.5) inside the JVM: no key, no network,
about 2 seconds the first time a command needs it.

The agent sets priorities, links alerts, watchlists accounts and creates tasks on its own; it can only
propose closing or escalating a case, which an analyst approves or rejects.

Every command accepts `--json` for machine-readable output. The database is `tracewise.db` in the
current directory unless `--db FILE` or `TRACEWISE_DB` says otherwise.

`./tracewise` with no command opens an interactive shell. Each line runs one of the commands above
(without `./tracewise`); a line that does not start with a command is a question for the assistant, which
keeps the conversation. Tab completes commands and options, Up and Down recall earlier lines, `help` lists
the commands, Ctrl+C stops a running investigation or triage, and `exit` or Ctrl+D quits. Root options go
on the command that opens the shell, for example `./tracewise --role supervisor`.

On a terminal the output is coloured (amber labels, red, yellow and green beside the word they repeat);
piped output and `NO_COLOR=1` give plain text.

### Standalone CLI: run the agent on a task file

`./mvnw package` builds one self-contained jar, `cli/target/tracewise.jar`; `./tracewise` is a shortcut for
`java -jar cli/target/tracewise.jar`. Run it from the repository folder so it finds `.env` and `data/`.
`--input FILE` runs a task and exits: each line is a command from the list above (without `./tracewise`), or a
question in plain words for the assistant; `#` starts a comment and `-` reads the task from standard input.
The exit code is 0 when every line succeeded and 1 otherwise.

```bash
java -jar cli/target/tracewise.jar --db task.db --input data/samples/task.txt
echo "Which cases are waiting for approval?" | java -jar cli/target/tracewise.jar --db task.db --input -
java -jar cli/target/tracewise.jar --db task.db --llm replay --model claude-haiku-5-5 --input data/samples/task.txt
```

[`data/samples/task.txt`](data/samples/task.txt) loads the sample data, runs the rules, has the agent investigate
ALR-00001 and asks the assistant about the account. The third line runs the same task without a key, from the
recorded runs (on a new database).

Options (all can go before `--input` or any command):

| Option | Environment variable | Default | Meaning |
|---|---|---|---|
| `--input FILE` | | | Run the task in FILE (`-`: standard input) and exit |
| `--llm PROVIDER` | `TRACEWISE_LLM` | `anthropic` | `anthropic` (key `ANTHROPIC_API_KEY` in `.env`), `openai` for any OpenAI-compatible API (`OPENAI_BASE_URL` and `OPENAI_API_KEY` in `.env`), or `replay` (recorded runs, no key) |
| `--model ID` | `TRACEWISE_MODEL` | `claude-sonnet-5-5` | The model, for example `claude-haiku-5-5` |
| `--db FILE` | `TRACEWISE_DB` | `tracewise.db` | The SQLite database |
| `--role ROLE` | `TRACEWISE_ROLE` | `analyst` | `analyst` or `supervisor` (only a supervisor changes rules) |
| `--record` | `TRACEWISE_RECORD` | off | Save every model answer to `data/replays` so the run can be replayed |
| `--replays DIR` | | `data/replays` | Folder of recorded runs |
| `--json` | | | On any command: the result as JSON, for scripts and test harnesses |
| `-h`, `--help`, `-V` | | | Help (also for each command: `tracewise investigate --help`) and version |

### GUI

```bash
./mvnw -q package -DskipTests
./tracewise-gui                                   # same settings as the CLI (TRACEWISE_DB, TRACEWISE_ROLE, ...)
TRACEWISE_ROLE=supervisor ./tracewise-gui         # a supervisor can change monitoring rules
TRACEWISE_LLM=replay TRACEWISE_MODEL=claude-haiku-5-5 ./tracewise-gui   # replay the recordings, no key
```

Screens: **Alerts** (import, monitoring, the queue, the Threshold Report Register, investigate, triage),
**Cases** (finding with verified claims, the agent's steps live, evidence, tasks, notes, network, report,
history, close and reopen), **Approvals** (decide the agent's proposals), **Admin** (rules, knowledge search,
audit log with CSV export, performance) and the **Assistant** panel beside every screen.

Keys: Ctrl+1 to 4 (Cmd on macOS) switch screens, Ctrl+K opens the command bar (a screen code such as `APRV`, or
an id such as `CAS-00001`), F2 investigates the selected alert, F3 starts triage, F9 and F10 approve and reject
on Approvals, F5 refreshes. The Day / Night label in the bottom bar switches the theme (`TRACEWISE_THEME=day`).
`TRACEWISE_SNAPSHOT=shots ./tracewise-gui` saves a PNG of every screen and exits.

### Configuration and API key (plug in your own)

The agent features (investigate, triage, ask, report draft) call an LLM; everything else runs without one.

**1. Claude (the default).** Put an Anthropic API key in `.env` in the repository folder:

```bash
cp .env.example .env      # .env is gitignored
# then edit .env:  ANTHROPIC_API_KEY=sk-ant-...
```

An environment variable works too (`export ANTHROPIC_API_KEY=...`); `.env` wins when both are set.
The default model is `claude-sonnet-5-5`; `--model claude-haiku-5-5` is much cheaper (about CAD 0.006 per investigation against about CAD 0.09).

**2. Check that it works** (a new database, so your own data is not touched; about 20 seconds, under CAD 0.01
with Haiku):

```bash
./tracewise --db check.db import data/samples/scenarios.csv
./tracewise --db check.db monitor run
./tracewise --db check.db --model claude-haiku-5-5 investigate ALR-00001
```

A working key lists the agent's steps as it takes them and ends with a verdict (for ALR-00001, ESCALATE),
`Checked VER n/n`, and the tokens and cost used. A wrong or expired key ends with `Run FAILED` and the
provider's message (`authentication_error: invalid x-api-key`).

**3. Another provider.** Any API that speaks the OpenAI chat-completions format (OpenAI itself, a local
server such as vLLM or Ollama, or another provider's OpenAI-compatible endpoint) works with `--llm openai`.
Put its address and key in `.env`:

```bash
OPENAI_BASE_URL=https://api.openai.com/v1     # the provider's OpenAI-compatible address
OPENAI_API_KEY=...                            # leave out for a local server without keys
```

then pass its model name: `./tracewise --llm openai --model <model id> investigate ALR-00001`. Prices are
known only for Claude models, so with another provider no cost is shown and `--max-cost` does not apply
(the CLI says so). This path is covered by tests against a stand-in server; the recorded runs, the
videos and the measurements in the report all used Claude.

**GUI.** The GUI reads the same settings from environment variables:
`TRACEWISE_MODEL=claude-haiku-5-5 ./tracewise-gui`, or `TRACEWISE_LLM=openai TRACEWISE_MODEL=<model id> ./tracewise-gui`.

**Windows.** `./tracewise` and `./tracewise-gui` are shell scripts; on Windows build with `mvnw.cmd package`
and run from the repository folder:

```bat
java -jar cli\target\tracewise.jar --db check.db import data\samples\scenarios.csv
java -cp gui\target\tracewise-gui.jar tracewise.ui.gui.GuiMain
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
./tracewise --llm replay --model claude-haiku-5-5 ask "Why is account C1037 risky?"   # after the triage
```

The recorded assistant questions (after the triage above) are listed in
`core/src/test/java/tracewise/app/CommittedReplaysTest.java`.

Replays answer only the recorded requests (same prompt, data and tool results). Retrieval results
are left out of the match, because the local embedding model's scores differ slightly between Apple
Silicon and x86 CPUs, and similar cases change as cases are decided. Rejecting a proposal changes the
next run's prompt, so a re-investigation after a rejection needs a live model.

Each investigation prints its token usage and cost in CAD.

### Sample data

`data/samples/scenarios.csv` is synthetic, generated with a fixed seed by
`tracewise.tools.ScenarioGenerator`. It plants one scheme per monitoring rule, boundary cases
that must not fire, and 60 normal customers that trigger nothing:

```bash
java -cp cli/target/tracewise.jar tracewise.tools.ScenarioGenerator 42 data/samples/scenarios.csv
```

TraceWise also reads IBM's synthetic AML data (Altman et al., NeurIPS 2023), which is not committed:
download `HI-Small_Trans.csv` from the Kaggle dataset `ealtman2019/ibm-transactions-for-anti-money-laundering-aml`
and run `./tracewise import HI-Small_Trans.csv`. The agent is measured on SAML-D (Oztas et al., IEEE ICEBE 2023;
CC BY-NC-SA 4.0), also not committed (Kaggle dataset `berkanoztas/synthetic-transaction-monitoring-dataset-aml`):
`tracewise.tools.SchemeSampler samld <SAML-D.csv> <out.csv> <schemes> <otherAccounts> <part> <seed>` samples whole
laundering schemes with their accounts' full history, `./tracewise import` and `monitor run` load and monitor the
sample, and `python3 -I scripts/evaluate.py <monitored.db> <alerts.txt> <out-dir> <model>` investigates a fixed alert
list, each on a fresh copy of the database, and scores it with `tracewise metrics`. The full file (5.08 million rows) takes about 12 minutes; rows in
Bitcoin, Ruble, Shekel and Riyal are rejected because the Bank of Canada publishes no rate for them.

## Stack

Java 21, JavaFX (GUI), picocli (CLI), LangChain4j (LLM access), Anthropic Claude models,
SQLite, JUnit, Maven.

## Repository layout

```
core/                      tracewise-core: all logic, no user interface (app with TraceWiseFacade, ingest,
                           monitoring, agent, llm, verification, cases, domain, persistence, audit, tools)
cli/                       tracewise-cli: picocli commands over the facade (cli/target/tracewise.jar)
gui/                       tracewise-gui: JavaFX views over the facade (gui/target/tracewise-gui.jar)
<module>/src/test/java/    JUnit tests
data/samples/              synthetic sample data
docs/
  stage1/                  Stage 1 design report; UML in UMLet (umlet/), earlier PlantUML drafts (diagrams/)
  stage2/                  Stage 2 plan, feature status, design-change log
  ai-usage/                AI tools used and the AI-human collaboration log
```
