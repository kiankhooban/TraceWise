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
| Stage 1: Design | [Design report](docs/stage1/Stage1-Design-Report.md) | In progress |
| Stage 2: Implementation | Source code, AI collaboration log | Not started |
| Stage 3: Testing | JUnit tests, KUMA agent behaviour tests | Not started |

## Planned stack

Java 21, JavaFX (GUI), picocli (CLI), LangChain4j (LLM access), Anthropic Claude models,
SQLite, JUnit 5, Maven.

## Repository layout

```
docs/
  stage1/          Stage 1 design report and UML diagrams (UMLet .uxf sources + exported .png)
  ai-usage/        AI tools used and the AI-human collaboration log
```
