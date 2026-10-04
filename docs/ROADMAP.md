# TraceWise Roadmap

## Phase 1: committed scope (Stage 2)

All 12 features specified in the Stage 1 design report (F01 to F12). These are built first and must
be complete before any stretch goal is started.

## Phase 2: stretch goals, planned once Phase 1 works

These were deliberately left out of the committed scope to keep the project achievable, but they
are planned. Visual items come first, because they are what a reviewer sees first in a demo.

The Stage 1 design already contains an extension point for each, so adding one extends the design
instead of changing it. Each addition will still be recorded in the Stage 2 design-change table
(Original design → Change → Reason).

| Priority | Stretch goal | Visible? | Extension point in the Stage 1 design |
|---|---|---|---|
| 1 | Live highlighting of the money-flow graph while the agent investigates | Yes | The network view observes `AgentTrace` events (Observer), the same events that drive the Agent Steps panel |
| 2 | Charts on the agent performance dashboard | Yes | The dashboard view renders metrics computed by a separate metrics component, so charts are a new view over the same data |
| 3 | PDF report export | Yes | Report export goes through a `ReportExporter` interface; PDF is one more implementation next to HTML and JSON |
| 4 | Cycle-detection rule (R7) | Indirectly | Each rule is a separate implementation of the detection-rule interface (Strategy); R7 is one more class |
| 5 | Calibration of rules R3 to R6 against the labelled IBM data | In reported results | Per-rule precision is already computed by F12 |
| 6 | Adversarial "red-team" mode | Yes | Requires instructor approval first. Would reuse the agent loop, the scenario format and the rule interface |
