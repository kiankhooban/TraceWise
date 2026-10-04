# TraceWise: Stage 1 Design Report

| | |
|---|---|
| **Course** | EECS3311 Software Design, Fall 2026, York University |
| **Author** | Kian Khooban |
| **Repository** | https://github.com/kiankhooban/TraceWise |

This report follows the structure of the EECS3311 Fall 2026 Stage 1 instructions: Task 1 (project
definition), Task 2 (UML design), Task 3 (feature-to-design traceability) and Task 4 (feature
realization).

## Contents

* [Task 1: Define Your Agent Project](#task-1-define-your-agent-project)
  * [1.1 Project Description](#11-project-description)
  * [1.2 Feature Specification](#12-feature-specification)
* [Task 2: Design Your System Using UML](#task-2-design-your-system-using-uml)
  * [2.1 Class Diagram and Design Patterns](#21-class-diagram-and-design-patterns)
  * [2.2 Use-Case Diagram and Use-Case Descriptions](#22-use-case-diagram-and-use-case-descriptions)
  * [2.3 Sequence Diagrams](#23-sequence-diagrams)
* [Task 3: Feature-to-Design Traceability](#task-3-feature-to-design-traceability)
* [Task 4: Explain How Each Feature Is Realized](#task-4-explain-how-each-feature-is-realized)

### Where each Stage 1 deliverable is

| # | Deliverable (from the Stage 1 instructions) | Location in this report |
|---|---|---|
| 1 | Project Overview: problem and motivation, target users, agent description, AI/LLM model(s), overall architecture | 1.1 |
| 2 | Detailed Feature Specifications | 1.2 |
| 3 | UML Class Diagram | 2.1 |
| 4 | Design Pattern Explanations | 2.1 |
| 5 | Use-Case Diagram | 2.2 |
| 6 | Detailed Use-Case Descriptions | 2.2 |
| 7 | Sequence Diagrams | 2.3 |
| 8 | Feature-to-Design Traceability Table | Task 3 |
| 9 | Feature Implementation Explanations | Task 4 |

---

# Task 1: Define Your Agent Project

TraceWise is an AI agent that investigates anti-money-laundering (AML) alerts. A deterministic rules
engine flags suspicious transactions. An LLM agent investigates each alert by planning its steps,
calling tools over the transaction ledger, retrieving matching FINTRAC money-laundering indicators,
and drafting a case summary. A deterministic verifier checks every amount, date and transaction ID
the agent cites, and a human analyst makes the final decision.

The project meets the Task 1 minimum requirements as follows:

| Requirement | How TraceWise meets it |
|---|---|
| GUI | JavaFX desktop application covering all major functionality |
| CLI | picocli command-line interface over the same core, including a machine-readable JSON mode |
| At least 10 meaningful features | 12 features, specified in 1.2 |
| At least 5 design patterns | Specified and justified in 2.1 |
| AI model | Anthropic Claude models through LangChain4j (1.1.5) |
| Agent behavior | Planning, tool use, retrieval, memory, decision making, multi-step investigation, and autonomous actions under permission tiers (1.1.3) |

## 1.1 Project Description

### 1.1.1 What problem does the project solve?

Canadian banks are legally required to report certain transactions to FINTRAC, the federal
financial intelligence agency. Three report types drive most of the work:

| Report | When it is required |
|---|---|
| Large Cash Transaction Report (LCTR) | Cash of $10,000 or more, received in one transaction or in several transactions within 24 hours for the same person (the "24-hour rule") |
| Electronic Funds Transfer Report (EFTR) | International electronic transfers of $10,000 or more, with the same 24-hour aggregation |
| Suspicious Transaction Report (STR) | Any amount, whenever there are "reasonable grounds to suspect" money laundering or terrorist financing |

LCTRs and EFTRs are mechanical: a threshold is crossed or it is not. STRs are not. Before an STR can
be filed, an analyst has to investigate the alert. That means pulling the account's history,
checking who it sends money to and receives money from, comparing the activity against FINTRAC's
published money-laundering indicators, and writing a narrative that justifies the suspicion.

The problem TraceWise addresses is that this investigation is slow, repetitive and mostly spent
assembling evidence, not judging it. Every alert needs the same lookups, the same comparisons and
the same write-up, and the analyst does them by hand.

This is an active area for Canadian banks. TD launched an agentic AI system for mortgage
pre-adjudication on 2026-05-21, built so that a human underwriter still makes the decision. BMO is
among the first banks developing an AI financial-crimes investigation agent with FIS, announced
2026-05-04. OSFI's Guideline E-23 on model risk management, effective 2027-05-01, explicitly covers
AI/ML models and requires explainability and monitoring. TraceWise is a small-scale, synthetic-data
version of this kind of system.

### 1.1.2 Who are the target users?

| User | Role | What they do with TraceWise |
|---|---|---|
| **AML Analyst** (primary) | Investigates alerts at a fictional Canadian bank | Loads transactions, works the alert queue, runs agent investigations and queue triage, reviews the evidence, approves or rejects the actions the agent proposes, exports reports |
| **Compliance Supervisor** | Oversees the analysts and the monitoring rules | Configures rule thresholds, reviews closed cases, monitors how accurate the agent is against labelled data |
| **Automation client** | A script or test harness (for example the KUMA test harness in Stage 3) | Drives the agent through the command-line interface and reads machine-readable results |

### 1.1.3 What can the agent do?

Given an alert, the TraceWise agent:

1. **Plans** an investigation based on the alert type and what it finds as it goes.
2. **Uses tools** to query the ledger: account history, counterparties, linked alerts, and
   aggregates over time windows.
3. **Retrieves** the FINTRAC money-laundering indicators that best match the evidence.
4. **Remembers** similar past cases and their outcomes, and uses them as context.
5. **Decides** on a recommended verdict (escalate or dismiss) with a stated confidence and reasons.
6. **Drafts** a case narrative and a suspicious-transaction report in structured form.

7. **Acts** on what it finds, within permission tiers (below).

The agent can also **triage the whole alert queue autonomously**. It works through every open alert
unattended (investigating, merging related alerts into cases, re-prioritising and drafting reports)
and finishes with a summary for the analyst, for example: "Processed 40 alerts. Merged 6 into 2
cases. 3 escalations await your approval. 22 proposed as false alarms."

The agent also answers free-text questions and requests from the analyst, such as "why is account
4412 risky?" or "show open structuring cases over $50,000".

**Permission tiers.** The agent takes low-risk actions on its own. High-impact actions are only
proposed, and wait in an approval queue until a human approves or rejects them. This follows the
human-in-the-loop approach banks use for agentic AI.

| Agent action | Tier |
|---|---|
| Set an alert's priority | Autonomous |
| Link related alerts into one case | Autonomous |
| Add an account to the watchlist for re-checking when new data arrives | Autonomous |
| Create a follow-up task for the analyst | Autonomous |
| Draft a suspicious-transaction report | Autonomous (draft only) |
| Close a case as a false alarm | Proposed; requires human approval |
| Escalate a case and finalise its report | Proposed; requires human approval |

Every claim the agent makes is checked by a deterministic verifier, and every action it takes or
proposes is recorded in the audit log.

### 1.1.4 Why is an AI agent appropriate for this problem?

An investigation cannot be scripted in advance: which evidence to gather next depends on what the
previous step found, and judging the evidence means matching it against indicator descriptions
written in natural language. Those are the parts an agent is good at. The parts where an error is
unacceptable stay in deterministic code.

| Task | Handled by | Reason |
|---|---|---|
| Deciding which evidence to gather next | **Agent (LLM)** | The right next step depends on what the previous step found; it cannot be fixed in advance |
| Interpreting evidence and matching it to laundering patterns | **Agent (LLM) + retrieval** | Requires judgment over unstructured indicator text |
| Writing the case narrative | **Agent (LLM)** | Natural-language synthesis |
| Answering analyst questions | **Agent (LLM)** | Open-ended natural-language interaction |
| Detecting threshold breaches (LCTR, EFTR, structuring) | **Deterministic rules** | Legal thresholds must be applied exactly, every time; LLMs make arithmetic errors over thousands of rows |
| Checking the agent's claims against the ledger | **Deterministic verifier** | The LLM cannot be trusted to check itself |
| Case status, audit log, report export | **Deterministic code** | Must be predictable and auditable |
| Final decision | **Human analyst** | Accountability stays with a person |

### 1.1.5 Which AI/LLM model(s) will be used?

| Model | Where it is used | Why |
|---|---|---|
| **Claude Sonnet 5.5** (`claude-sonnet-5-5`), Anthropic | Default model for the investigation agent and all other LLM tasks | Strong tool use at $2 / $10 per million input/output tokens (Anthropic pricing as of 2026-10-03). Fits the project budget, including repeated Stage 3 test runs |
| **Claude Opus 5.5** (`claude-opus-5-5`), Anthropic | Optional upgrade, selected through configuration | Higher capability at $4 / $20 per million tokens. Used in Stage 3 to compare reliability against Sonnet on the same cases |
| **all-MiniLM-L6-v2** embedding model, run in-process through LangChain4j | Retrieval of FINTRAC indicators and similar past cases | Runs locally inside the JVM, so retrieval needs no external service and costs nothing |
| **Low-cost OpenAI-compatible model** (for example DeepSeek) | Development only: exercising the plumbing (agent loop, tool calls, output parsing, GUI wiring) | Cheaper while the code itself is being debugged. Not used for prompt tuning or for any reported Stage 3 result, because behaviour does not transfer reliably between models |

All LLM access goes through **LangChain4j** behind TraceWise's own `LLMClient` interface, with
three implementations:

* `AnthropicLLMClient` for Claude models.
* `OpenAiCompatibleLLMClient` for any provider with an OpenAI-compatible API.
* `ReplayLLMClient`, which replays previously recorded model responses. It allows deterministic
  automated tests and lets the application be demonstrated without an API key.

The model is selected through configuration, so switching models requires no code change.

### 1.1.6 How will the AI model interact with the rest of the software system?

The LLM never touches the data directly. Its access is constrained in six ways:

1. **Tools only.** The agent can read and change data only through a fixed set of registered tools.
   Each tool validates its arguments before running.
2. **Actions are commands under a permission policy.** Every action the agent requests becomes a
   command object, the same kind of object a human action creates. A permission policy decides
   whether the command runs immediately (autonomous tier) or is placed in the approval queue for a
   human (approval tier). The agent has no way to bypass this check.
3. **Structured output.** The agent's conclusions are returned as a structured `AgentFinding`
   object (verdict, confidence, cited transactions, narrative), not free text.
4. **Verification.** The `ClaimVerifier` checks every amount, date and transaction ID in the
   finding against the database. Unsupported claims are marked as rejected and shown to the analyst.
5. **Bounded execution.** Each investigation has a maximum number of agent turns and a recorded
   token cost, so a confused agent cannot loop indefinitely.
6. **Full trace.** Every tool call, action, argument, result and model output is recorded
   in an `AgentTrace`. The trace feeds the audit log, the agent-steps panel in the GUI, and the
   machine-readable CLI output used for Stage 3 testing.

### 1.1.7 Overall architecture

TraceWise is a single Java application split into three Maven modules.

```
            +--------------------+        +--------------------+
            |   tracewise-gui    |        |   tracewise-cli    |
            |  JavaFX, MVC views |        | picocli commands,  |
            |  and controllers   |        | text and JSON mode |
            +---------+----------+        +---------+----------+
                      |                             |
                      +-------------+---------------+
                                    |  calls only the facade
                      +-------------v---------------+
                      |       tracewise-core        |
                      |                             |
                      |  TraceWiseFacade            |
                      |  domain model (accounts,    |
                      |    transactions, alerts,    |
                      |    cases)                   |
                      |  monitoring rules engine    |
                      |  agent: planner, tools,     |
                      |    memory, retrieval        |
                      |  claim verifier             |
                      |  persistence (DAO), audit   |
                      +------+---------------+------+
                             |               |
               +-------------v---+     +-----v-----------------+
               | SQLite database |     | Anthropic Claude API  |
               | (local file)    |     | via LangChain4j       |
               +-----------------+     +-----------------------+
```

* **tracewise-core** contains all logic and has no dependency on either user interface.
* **tracewise-gui** and **tracewise-cli** are two front ends over the same core, and both call it
  only through `TraceWiseFacade`.
* External dependencies are the Anthropic API (through LangChain4j), a local SQLite database file,
  transaction CSV files, and a text corpus of FINTRAC money-laundering indicators.

**Technology stack**

| Need | Technology |
|---|---|
| Language | Java 21 |
| Build | Maven, through the Maven Wrapper (`./mvnw`), so no separate Maven install is needed |
| GUI | JavaFX 21 |
| CLI | picocli |
| LLM access and embeddings | LangChain4j |
| Database | SQLite through JDBC |
| JSON and CSV | Jackson, Apache Commons CSV |
| Network graph view | JavaFXSmartGraph |
| Deterministic tests | JUnit 5, Mockito |
| Agent behaviour tests (Stage 3) | KUMA, through a small Python harness that calls the CLI's JSON mode |
| UML | UMLet |

### 1.1.8 Data

* **Transactions:** a sample of IBM's synthetic AML dataset (CSV, every transaction labelled as
  laundering or normal, CDLA-Sharing-1.0 licence). The labels let the agent's verdicts be measured
  against ground truth.
* **Test scenarios:** small hand-built CSV files with planted patterns, such as three cash deposits
  of $9,500 within 24 hours, where the correct outcome is known exactly.
* **Indicator corpus:** FINTRAC's published money-laundering and terrorist-financing indicators for
  financial entities.

No real customer data is used at any point.

### 1.1.9 Scope and non-goals

* TraceWise does not file anything with FINTRAC. Report "filing" produces a local document only.
* TraceWise is not legal or compliance advice. Its rules are modelled on FINTRAC's published rules
  for learning purposes.
* **Future work, pending instructor approval:** an adversarial "red-team" mode, in which a second
  agent designs laundering schemes intended to evade the rules, and the investigator agent proposes
  new rules in response.

### 1.1.10 Use of AI in preparing this report

AI tools are part of this project by design. Claude Code (model Claude Opus 5.5) was used to analyse
the course specification, research the domain, and draft this report and its UML diagrams. Every
design decision was reviewed, and in several cases changed, by the author. Details are recorded in
[`docs/ai-usage/`](../ai-usage/).

## 1.2 Feature Specification

*To be completed.*

---

# Task 2: Design Your System Using UML

## 2.1 Class Diagram and Design Patterns

*To be completed.*

## 2.2 Use-Case Diagram and Use-Case Descriptions

*To be completed.*

## 2.3 Sequence Diagrams

*To be completed.*

---

# Task 3: Feature-to-Design Traceability

*To be completed.*

---

# Task 4: Explain How Each Feature Is Realized

*To be completed.*

---

## Sources (accessed 2026-10-03)

* FINTRAC, 24-hour rule: https://fintrac-canafe.canada.ca/guidance-directives/transaction-operation/24hour/1-eng
* FINTRAC, suspicious transaction reporting: https://fintrac-canafe.canada.ca/guidance-directives/transaction-operation/str-dod/str-dod-eng
* FINTRAC, money-laundering indicators for financial entities: https://fintrac-canafe.canada.ca/guidance-directives/transaction-operation/indicators-indicateurs/fin_mltf-eng
* OSFI, Guideline E-23 Model Risk Management: https://www.osfi-bsif.gc.ca/en/guidance/guidance-library/guideline-e-23-model-risk-management-2027
* TD, agentic AI for real-estate secured lending (2026-05-21): https://td.mediaroom.com/2026-05-21-TD-Launches-Agentic-AI-to-Transform-Real-Estate-Secured-Lending-from-End-to-End
* FIS, financial crimes AI agent with Anthropic (2026-05-04): https://www.fisglobal.com/about-us/media-room/press-release/2026/fis-brings-agentic-ai-to-banking-with-anthropic-starting-with-financial-crimes
* IBM AML data: https://github.com/IBM/AML-Data
* Anthropic models and pricing: https://platform.claude.com/docs/en/about-claude/models/overview
