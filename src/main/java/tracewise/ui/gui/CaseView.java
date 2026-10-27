package tracewise.ui.gui;

import java.math.RoundingMode;
import java.util.List;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import tracewise.agent.AgentEventListener;
import tracewise.agent.AgentFinding;
import tracewise.agent.AgentOutcome;
import tracewise.agent.InvestigationResult;
import tracewise.agent.TraceStep;
import tracewise.agent.tools.SearchIndicatorsTool;
import tracewise.agent.tools.SimilarCasesTool;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Case;
import tracewise.retrieval.RetrievedPassage;
import tracewise.verification.ClaimCheck;

/**
 * F03, F04, F05, F08: the cases list and one case. Observer: during an investigation it receives each agent step
 * (onStep) and the end of the run (onFinished) and shows them as they happen.
 */
final class CaseView implements AgentEventListener {

    private static final String ALL = "All";

    private final Gui gui;
    private final TableView<Case> cases = new TableView<>();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final Label title = new Label("Select a case");
    private final Label subtitle = new Label();
    private final Button investigate = new Button("Investigate");
    private final Button cancel = new Button("Cancel");
    private final VBox finding = new VBox(8);
    private final TableView<TraceStep> steps = new TableView<>();
    private final TableView<RetrievedPassage> indicators = new TableView<>();
    private final TableView<RetrievedPassage> similar = new TableView<>();
    private final ListView<String> tasks = new ListView<>();
    private final TabPane detail = new TabPane();
    private Case shown;
    private String running;

    CaseView(Gui gui) {
        this.gui = gui;
    }

    Node build() {
        statusFilter.getItems().add(ALL);
        for (CaseStatus s : CaseStatus.values()) {
            statusFilter.getItems().add(s.name());
        }
        statusFilter.setValue(ALL);
        statusFilter.setOnAction(e -> refresh());
        var reload = new Button("Refresh");
        reload.setOnAction(e -> refresh());
        var listBar = new HBox(8, new Label("Status"), statusFilter, reload);
        listBar.getStyleClass().add("toolbar");
        cases.getColumns().setAll(List.of(Gui.column("Case", Case::id, 85), Gui.column("Status", Case::status, 150),
                Gui.column("Verdict", c -> c.latestFinding() == null ? "" : c.latestFinding().verdict(), 105)));
        cases.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        cases.setPlaceholder(new Label("No cases yet. Investigate an alert."));
        cases.getSelectionModel().selectedItemProperty().addListener((o, old, c) -> {
            if (c != null && (shown == null || !shown.id().equals(c.id()))) {
                showCase(c.id());
            }
        });
        var left = new BorderPane(cases);
        left.setTop(listBar);
        left.setMinWidth(340);

        title.getStyleClass().add("section-title");
        subtitle.getStyleClass().add("muted");
        investigate.getStyleClass().add("primary");
        investigate.setDisable(true);
        investigate.setOnAction(e -> investigate(shown.alertIds().get(0)));
        cancel.setDisable(true);
        cancel.setOnAction(e -> gui.facade.cancelInvestigation(running)); // only sets a flag: safe on the UI thread
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var header = new HBox(10, new VBox(2, title, subtitle), spacer, investigate, cancel);
        header.getStyleClass().add("toolbar");

        steps.getColumns().setAll(List.of(Gui.column("Turn", TraceStep::turn, 45), Gui.column("Tool", TraceStep::toolName, 140),
                Gui.column("Arguments", s -> oneLine(s.arguments(), 160), 260),
                Gui.column("Result", s -> (s.error() ? "ERROR: " : "") + oneLine(s.result(), 300), 420),
                Gui.column("ms", TraceStep::durationMs, 55)));
        steps.setPlaceholder(new Label("No agent steps."));
        indicators.getColumns().setAll(List.of(Gui.column("Score", RetrievedPassage::score, 60),
                Gui.column("FINTRAC indicator", RetrievedPassage::text, 520), Gui.column("Source", RetrievedPassage::source, 300)));
        indicators.setPlaceholder(new Label("The agent retrieved no indicators."));
        similar.getColumns().setAll(List.of(Gui.column("Score", RetrievedPassage::score, 60),
                Gui.column("Case", RetrievedPassage::source, 90), Gui.column("Decision and summary", RetrievedPassage::text, 720)));
        similar.setPlaceholder(new Label("No similar past case."));
        var evidence = new VBox(6, new Label("Indicators retrieved"), indicators, new Label("Similar past cases"), similar);
        VBox.setVgrow(indicators, Priority.ALWAYS);
        VBox.setVgrow(similar, Priority.ALWAYS);
        evidence.setPadding(new Insets(8));
        finding.setPadding(new Insets(10));
        detail.getTabs().setAll(new Tab("Finding", new javafx.scene.control.ScrollPane(finding)), new Tab("Agent steps", steps),
                new Tab("Evidence", evidence), new Tab("Tasks", tasks));
        detail.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        ((javafx.scene.control.ScrollPane) detail.getTabs().get(0).getContent()).setFitToWidth(true);
        var right = new BorderPane(detail);
        right.setTop(header);

        var split = new SplitPane(left, right);
        split.setDividerPositions(0.34);
        var root = new BorderPane(split);
        root.setPadding(new Insets(10));
        return root;
    }

    /** The case on screen, or null: the assistant's context. */
    String currentCaseId() {
        return shown == null ? null : shown.id();
    }

    void refresh() {
        String status = statusFilter.getValue();
        gui.run("Loading cases", gui.facade::listCases, all -> {
            cases.getItems().setAll(all.stream().filter(c -> ALL.equals(status) || c.status().name().equals(status)).toList());
            if (shown != null) {
                showCase(shown.id());
            } else if (!cases.getItems().isEmpty()) {
                cases.getSelectionModel().selectFirst();
            }
        });
    }

    void showCase(String caseId) {
        gui.run("Loading " + caseId, () -> gui.facade.getCase(caseId), this::show);
    }

    /** Runs the agent on the alert; its steps appear live, then the case is shown with the finding. */
    void investigate(String alertId) {
        running = alertId;
        steps.getItems().clear();
        detail.getSelectionModel().select(1);
        title.setText("Investigating " + alertId);
        subtitle.setText("The agent's steps appear as they happen.");
        investigate.setDisable(true);
        cancel.setDisable(false);
        gui.worker.submit("Investigating " + alertId, () -> gui.facade.investigate(alertId, this), this::finished, e -> {
            ended();
            gui.error(e);
        });
    }

    private void finished(InvestigationResult r) {
        ended();
        if (r.finding() == null) {
            gui.info("Investigation ended " + r.status(), r.message());
        }
        refresh();
        showCase(r.caseId());
        detail.getSelectionModel().select(r.finding() == null ? 1 : 0);
    }

    private void ended() {
        running = null;
        cancel.setDisable(true);
        investigate.setDisable(shown == null || decided(shown));
    }

    @Override
    public void onStep(TraceStep step) {
        Platform.runLater(() -> steps.getItems().add(step)); // called on the worker thread
    }

    @Override
    public void onFinished(AgentOutcome outcome) {
    }

    private void show(Case c) {
        shown = c;
        title.setText(c.id() + "   " + c.status());
        subtitle.setText("Alerts " + String.join(", ", c.alertIds()) + "   opened " + Gui.text(c.openedAt()) + " UTC");
        investigate.setDisable(running != null || decided(c));
        AgentFinding f = c.latestFinding();
        tasks.getItems().setAll(c.tasks().stream().map(t -> (t.done() ? "[done] " : "[open] ") + t.description()).toList());
        finding.getChildren().clear();
        if (f == null) {
            finding.getChildren().add(new Label("No finding yet. Investigate the alert to let the agent work on it."));
            if (running == null) {
                steps.getItems().clear();
            }
            indicators.getItems().clear();
            similar.getItems().clear();
            return;
        }
        if (running == null) {
            steps.getItems().setAll(f.trace().steps());
        }
        var byScore = java.util.Comparator.comparingDouble(RetrievedPassage::score).reversed();
        indicators.getItems().setAll(SearchIndicatorsTool.retrieved(f.trace().steps()).stream().sorted(byScore).toList());
        similar.getItems().setAll(SimilarCasesTool.retrieved(f.trace().steps()).stream().sorted(byScore).toList());
        var verdict = new Label(f.verdict() + "   confidence " + f.confidence() + (f.incomplete() ? "   (INCOMPLETE)" : ""));
        verdict.getStyleClass().add("verdict-" + f.verdict());
        var report = f.verification();
        var verification = new Label("Verification: " + report.status() + ", " + report.summary());
        var narrative = new TextArea(f.narrative());
        narrative.setWrapText(true);
        narrative.setEditable(false);
        narrative.setPrefRowCount(6);
        var checks = new TableView<ClaimCheck>(FXCollections.observableArrayList(report.checks()));
        checks.getColumns().setAll(List.of(Gui.column("", k -> k.confirmed() ? "OK" : "REJECTED", 80),
                Gui.column("Claim", ClaimCheck::claim, 560), Gui.column("Why rejected", ClaimCheck::reason, 260)));
        checks.setPrefHeight(Math.min(320, 34 + 26 * report.checks().size()));
        var t = f.trace();
        var cost = new Label("Model " + t.pricing().modelName() + ": " + t.usage().inputTokens() + " input + "
                + t.usage().outputTokens() + " output tokens, CAD " + t.costCad().setScale(4, RoundingMode.HALF_UP));
        cost.getStyleClass().add("muted");
        finding.getChildren().addAll(verdict, verification, section("Narrative"), narrative, section("Claims checked"),
                checks, section("Indicators the agent cited"),
                new Label(f.indicators().isEmpty() ? "None" : "- " + String.join("\n- ", f.indicators())), cost);
    }

    private static Label section(String text) {
        var l = new Label(text);
        l.getStyleClass().add("section-title");
        return l;
    }

    /** Only an open case or one under investigation can be investigated (the case states allow nothing else). */
    private static boolean decided(Case c) {
        return c.status() != CaseStatus.OPEN && c.status() != CaseStatus.UNDER_INVESTIGATION;
    }

    private static String oneLine(String s, int max) {
        String one = s == null ? "" : s.replaceAll("\\s+", " ");
        return one.length() > max ? one.substring(0, max) + "..." : one;
    }
}
