# AI-Human Collaboration Log

Representative interactions, in the format required by the project instructions (Stage 2, section 10).

---

## Entry 1: Choosing the project (2026-09-29 to 2026-10-03)

**Task:** Choose a project that satisfies the EECS3311 Stage 1 requirements and is relevant to roles at Canadian banks.

**Input/Instruction:** Asked Claude Code to analyse the Stage 1 specification and propose project ideas other than the 20 sample projects, then to research what Canadian Big 5 banks look for in software and data roles.

**AI Contribution:** Summarised the specification requirements. Proposed five ideas built around a deterministic verifier, so the agent's output can be checked automatically. Researched bank job postings, bank AI initiatives (TD's agentic mortgage system, BMO's financial-crimes agent pilot), FINTRAC reporting thresholds, OSFI Guideline E-23, and licensed synthetic AML datasets. Recommended an AML alert-investigation agent.

**Human Contribution:** Rejected generic ideas and redirected the search toward a bank-relevant project. Confirmed with the instructor that the project must be Java only, which ruled out a React frontend. Removed the proposed "adversarial red-team" feature to keep the scope achievable, and moved it to future work pending instructor approval. Rejected the name "Ledgerhound" as hard to read and chose "TraceWise". Questioned the LLM budget, which led to Claude Sonnet 5.5 becoming the default model, with Opus 5.5 as an upgrade.

**Outcome:** The project is TraceWise, a Java AML investigation agent with a JavaFX GUI, a picocli CLI, and LangChain4j.

---

## Entry 2: Stage 3 testing constraints shaping the Stage 1 design (2026-10-03)

**Task:** Make sure the Stage 1 design can support the Stage 3 KUMA agent testing.

**Input/Instruction:** Shared the full project instructions and asked Claude Code to check what the later stages require.

**AI Contribution:** Read the KUMA SDK documentation. KUMA is Python, does not run the agent itself, and captures traces only from code in the same process. Claude Code concluded that the Java agent must expose a machine-readable CLI mode that returns its own tool-call trace. It proposed design additions: `AgentTrace`/`ToolCallRecord` classes, a plain-text agent entry point, and a fault-injecting tool decorator for testing unavailable or malformed tools.

**Human Contribution:** Confirmed that a small Python harness for KUMA is acceptable, since KUMA is mandated by the course. Confirmed that API keys for KUMA are available.

**Outcome:** These additions are included in the Stage 1 class diagram.
