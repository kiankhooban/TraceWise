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

---

## Entry 3: UML diagrams and design review (2026-10-03 to 2026-10-04)

**Task:** Produce the Stage 1 UML (use-case, class and sequence diagrams) following the EECS3311 UML
lecture conventions.

**Input/Instruction:** Asked Claude Code to draw every diagram, check each rendered PNG individually,
and follow the lecture's notation rules exactly.

**AI Contribution:** Chose PlantUML over UMLet for automatic layout and single-source consistency,
after rendering notation test sheets. Built a master class model with tag-filtered views, rendered
and inspected every diagram, and caught its own errors during review: attributes duplicating
association lines, a missing multiplicity hidden behind an arrowhead, misleading labels from
orthogonal routing, missing «interface» stereotypes, and several sequence diagrams where an `alt` or
`break` fragment let a failed path continue as if it had succeeded.

**Human Contribution:** Rejected an initial use-case diagram layout and a class-diagram presentation
that was too large to read. Brought in an independent reviewer's critique, which led to a new main
class diagram of major classes, with the detailed views kept as supporting material. Required that
each diagram be checked individually before moving on.

**Outcome:** One use-case diagram, a main class diagram plus eight detailed views, and ten sequence
diagrams, all generated from PlantUML sources in `docs/stage1/diagrams/src/`.

---

## Entry 4: Redrawing the UML in UMLet (2026-10-05 to 2026-10-06)

**Task:** Redraw all Stage 1 UML in UMLet, the tool the course requires, without changing the design.

**Input/Instruction:** Told Claude Code that the diagrams must be made in UMLet, that the design and
scope must not change (only how much of it is drawn), that the PlantUML drafts stay in the repository,
and that every diagram must again be checked individually as a PNG against the Task 2 requirements.

**AI Contribution:** Wrote Python generators that produce UMLet `.uxf` files (a 52-class model drawn
as four class-diagram figures with grey reference boxes, the use-case diagram, and nine sequence
diagrams), exported them with UMLet itself, and inspected each PNG. Added automated checks: no UMLet
parse errors, every sequence-diagram message is a method of the receiving class on the class
diagram, and every message inside a combined fragment stays within the fragment's lifelines. The
visual review and the checks caught errors it then fixed: rule configuration initiated by the wrong
actor, a triage stop drawn after the triage had finished, export drawn as unconditional although it
is an extension, approve and reject drawn as if both happened, a tool call routed through a class
with no association to the retriever, and fragment frames that did not cover the lifelines they
contained. Updated the report's Task 2, 3 and 4 to use only names on the UMLet diagrams.

**Human Contribution:** Brought the UMLet requirement from the course. Rejected an attempt to shrink
the design itself to make the diagrams smaller ("we are just changing the UML, not the actual
structure of the project"), chose the four-figure split, and required the per-diagram checks.

**Outcome:** The report now shows only UMLet diagrams (`docs/stage1/umlet/`), and the PDF is
regenerated with `docs/stage1/build_pdf.py`. The PlantUML drafts remain in `docs/stage1/diagrams/`.

