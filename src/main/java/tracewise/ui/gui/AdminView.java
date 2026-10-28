package tracewise.ui.gui;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import tracewise.audit.AuditEntry;
import tracewise.audit.AuditFilter;
import tracewise.domain.ActorType;
import tracewise.domain.DateRange;
import tracewise.metrics.MetricRow;
import tracewise.metrics.MetricsCsv;
import tracewise.metrics.MetricsFilter;
import tracewise.metrics.PerformanceMetrics;
import tracewise.monitoring.DetectionRule;
import tracewise.monitoring.RuleConfig;
import tracewise.retrieval.RetrievedPassage;

/** Rules (F02, supervisors change them), knowledge search (F04), the audit log (F08) and performance (F12). */
final class AdminView {

    private final Gui gui;
    private final TableView<DetectionRule> rules = new TableView<>();
    private final TableView<RetrievedPassage> knowledge = new TableView<>();
    private final TextField query = new TextField();
    private final TableView<AuditEntry> audit = new TableView<>();
    private final ComboBox<String> eventType = new ComboBox<>();
    private final TextField target = new TextField();
    private final javafx.scene.control.DatePicker from = new javafx.scene.control.DatePicker();
    private final javafx.scene.control.DatePicker to = new javafx.scene.control.DatePicker();
    private final ComboBox<String> actor = new ComboBox<>(FXCollections.observableArrayList("Anyone", "ANALYST",
            "SUPERVISOR", "AGENT", "SYSTEM"));
    private final TableView<List<String>> metrics = new TableView<>();
    private final ComboBox<String> metricsModel = new ComboBox<>(FXCollections.observableArrayList(ALL_MODELS));
    private final ComboBox<String> metricsRule = new ComboBox<>(FXCollections.observableArrayList("All rules", "R1",
            "R2", "R3", "R4", "R5", "R6"));
    private final javafx.scene.control.DatePicker metricsFrom = new javafx.scene.control.DatePicker();
    private final javafx.scene.control.DatePicker metricsTo = new javafx.scene.control.DatePicker();
    private final Label verdicts = new Label();
    /** What the table shows, for the CSV export. */
    private List<PerformanceMetrics> shown = List.of();
    private static final String ALL_MODELS = "All models";

    AdminView(Gui gui) {
        this.gui = gui;
    }

    Node build() {
        var tabs = new TabPane(new Tab("Rules", rulesPane()), new Tab("Knowledge", knowledgePane()),
                new Tab("Audit Log", auditPane()), new Tab("Performance", performancePane()));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        var root = new BorderPane(tabs);
        root.setPadding(new Insets(10));
        return root;
    }

    void refresh() {
        gui.run("Loading rules", gui.facade::listRules, list -> rules.getItems().setAll(list));
        searchAudit();
        onShowMetrics(metricsFilter());
    }

    private Node rulesPane() {
        rules.getColumns().setAll(List.of(Gui.column("Rule", DetectionRule::id, 60), Gui.column("Kind", DetectionRule::kind, 110),
                Gui.column("Enabled", r -> r.config().enabled() ? "yes" : "no", 70),
                Gui.column("Parameters", r -> new TreeMap<>(r.config().parameters()).entrySet().stream()
                        .map(e -> e.getKey() + " = " + e.getValue().toPlainString()).collect(Collectors.joining(",   ")), 700)));
        var edit = new Button("Edit rule...");
        boolean supervisor = gui.config.userRole() == ActorType.SUPERVISOR;
        edit.setDisable(!supervisor);
        edit.setOnAction(e -> editRule(rules.getSelectionModel().getSelectedItem()));
        var note = new Label(supervisor ? "Changes apply to the next monitoring run and are audited."
                : "Only a Compliance Supervisor can change rules (start with TRACEWISE_ROLE=SUPERVISOR).");
        note.getStyleClass().add("muted");
        var bar = new HBox(10, edit, note);
        bar.getStyleClass().add("toolbar");
        var pane = new BorderPane(rules);
        pane.setTop(bar);
        pane.setPadding(new Insets(8));
        return pane;
    }

    private void editRule(DetectionRule rule) {
        if (rule == null) {
            gui.info("Select a rule", "Select the rule to change.");
            return;
        }
        var dialog = new Dialog<RuleConfig>();
        dialog.initOwner(gui.stage);
        dialog.setTitle("Edit " + rule.id());
        dialog.setHeaderText(rule.id() + " (" + rule.kind() + ")");
        var grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        var enabled = new CheckBox("Enabled");
        enabled.setSelected(rule.config().enabled());
        grid.add(enabled, 0, 0, 2, 1);
        var fields = new TreeMap<String, TextField>();
        int row = 1;
        for (var p : new TreeMap<>(rule.config().parameters()).entrySet()) {
            var field = new TextField(p.getValue().toPlainString());
            fields.put(p.getKey(), field);
            grid.add(new Label(p.getKey()), 0, row);
            grid.add(field, 1, row++);
        }
        var problem = new Label();
        problem.getStyleClass().add("verdict-ESCALATE");
        grid.add(problem, 0, row, 2, 1);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        // checked before the dialog closes, so a bad value keeps it open with a message
        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            for (var f : fields.entrySet()) {
                try {
                    new BigDecimal(f.getValue().getText().strip());
                } catch (NumberFormatException ex) {
                    problem.setText(f.getKey() + " must be a number");
                    e.consume();
                    return;
                }
            }
        });
        dialog.setResultConverter(b -> {
            if (b != ButtonType.OK) {
                return null;
            }
            var parameters = new HashMap<String, BigDecimal>();
            fields.forEach((name, f) -> parameters.put(name, new BigDecimal(f.getText().strip())));
            return new RuleConfig(enabled.isSelected(), parameters);
        });
        dialog.showAndWait().ifPresent(config -> gui.run("Saving " + rule.id(), () -> {
            gui.facade.updateRule(rule.id(), config);
            return null;
        }, r -> refresh()));
    }

    private Node knowledgePane() {
        knowledge.getColumns().setAll(List.of(Gui.column("Score", RetrievedPassage::score, 60),
                Gui.column("Indicator or past case", RetrievedPassage::text, 640), Gui.column("Source", RetrievedPassage::source, 320)));
        knowledge.setPlaceholder(new Label("Search FINTRAC's indicators and the decided cases in plain words."));
        query.setPromptText("deposits below the reporting threshold");
        query.setOnAction(e -> search());
        var go = new Button("Search");
        go.getStyleClass().add("primary");
        go.setOnAction(e -> search());
        HBox.setHgrow(query, Priority.ALWAYS);
        var bar = new HBox(8, query, go);
        bar.getStyleClass().add("toolbar");
        var pane = new BorderPane(knowledge);
        pane.setTop(bar);
        pane.setPadding(new Insets(8));
        return pane;
    }

    private void search() {
        String q = query.getText();
        if (q.isBlank()) {
            return;
        }
        gui.run("Searching", () -> gui.facade.searchKnowledge(q), results -> {
            knowledge.getItems().setAll(results);
            knowledge.setPlaceholder(new Label("Nothing relevant found."));
        });
    }

    private Node auditPane() {
        eventType.setItems(FXCollections.observableArrayList("All", "IMPORT", "MONITORING_RUN", "RULE_UPDATED",
                "INVESTIGATION_STARTED", "INVESTIGATION_FINISHED", "AGENT_STEP", "AGENT_FINISHED", "ACTION_EXECUTED",
                "ACTION_PROPOSED", "ACTION_REJECTED", "PROPOSAL_APPROVED", "PROPOSAL_REJECTED", "PROPOSAL_EXPIRED",
                "TRIAGE_FINISHED", "ASSISTANT_ASKED"));
        eventType.setValue("All");
        target.setPromptText("case, alert or account");
        target.setPrefColumnCount(9);
        from.setPromptText("from");
        to.setPromptText("to");
        from.setPrefWidth(120);
        to.setPrefWidth(120);
        actor.setValue("Anyone");
        var go = new Button("Filter");
        go.setOnAction(e -> searchAudit());
        var export = new Button("Export CSV...");
        export.setOnAction(e -> exportAudit());
        var bar = new HBox(8, new Label("Event"), eventType, new Label("Target"), target, new Label("Actor"), actor,
                from, to, go, export);
        bar.getStyleClass().add("toolbar");
        audit.getColumns().setAll(List.of(Gui.column("Time (UTC)", AuditEntry::timestamp, 130),
                Gui.column("Actor", e -> e.actor().name() + " (" + e.actor().type() + ")", 150),
                Gui.column("Event", AuditEntry::eventType, 150), Gui.column("Target", AuditEntry::target, 100),
                Gui.column("Details", e -> e.details() == null ? "" : e.details().replaceAll("\\s+", " "), 500),
                Gui.column("Outcome", AuditEntry::outcome, 90)));
        var pane = new BorderPane(audit);
        pane.setTop(bar);
        pane.setPadding(new Insets(8));
        return pane;
    }

    private AuditFilter auditFilter() {
        return new AuditFilter("All".equals(eventType.getValue()) ? null : eventType.getValue(),
                target.getText().isBlank() ? null : target.getText().strip(),
                from.getValue() == null ? null : from.getValue().atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
                to.getValue() == null ? null : to.getValue().plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
                "Anyone".equals(actor.getValue()) ? null : ActorType.valueOf(actor.getValue()));
    }

    private void searchAudit() {
        var filter = auditFilter();
        gui.run("Loading the audit log", () -> gui.facade.queryAudit(filter), entries -> audit.getItems().setAll(entries));
    }

    private void exportAudit() {
        var chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Export the audit log");
        chooser.setInitialFileName("tracewise-audit.csv");
        var file = chooser.showSaveDialog(gui.stage);
        if (file == null) {
            return;
        }
        var filter = auditFilter();
        gui.run("Exporting the audit log", () -> {
            try (var w = java.nio.file.Files.newBufferedWriter(file.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                return gui.facade.exportAudit(filter, w);
            }
        }, n -> gui.info("Audit log exported", n + " entries written to " + file));
    }

    /** F12: the Supervisor's Performance screen; one column per model when all models are selected. */
    private Node performancePane() {
        metricsModel.setValue(ALL_MODELS);
        metricsRule.setValue("All rules");
        metricsFrom.setPromptText("from");
        metricsTo.setPromptText("to");
        metricsFrom.setPrefWidth(120);
        metricsTo.setPrefWidth(120);
        var show = new Button("Show");
        show.getStyleClass().add("primary");
        show.setOnAction(e -> onShowMetrics(metricsFilter()));
        var export = new Button("Export CSV...");
        export.setOnAction(e -> exportMetrics());
        var bar = new javafx.scene.layout.FlowPane(8, 6, new Label("Model"), metricsModel, new Label("Rule"), metricsRule,
                metricsFrom, metricsTo, show, export);
        bar.getStyleClass().add("toolbar");
        metrics.setPlaceholder(new Label("No metrics yet."));
        metrics.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        verdicts.setWrapText(true);
        var note = new Label("Precision and recall compare each case's latest verdict with the laundering labels of its "
                + "alerts' transactions (data without labels shows \"not available\"). Rule precision is the share of a "
                + "rule's labelled alerts that involve laundering. n is what a figure rests on; under "
                + tracewise.metrics.Measure.MIN_SAMPLE + " it is marked too few.");
        note.getStyleClass().add("muted");
        note.setWrapText(true);
        var pane = new BorderPane(metrics);
        pane.setTop(bar);
        pane.setBottom(new javafx.scene.layout.VBox(4, verdicts, note));
        pane.setPadding(new Insets(8));
        return pane;
    }

    private MetricsFilter metricsFilter() {
        return new MetricsFilter(DateRange.ofDays(metricsFrom.getValue(), metricsTo.getValue()),
                ALL_MODELS.equals(metricsModel.getValue()) ? null : metricsModel.getValue(),
                "All rules".equals(metricsRule.getValue()) ? null : metricsRule.getValue());
    }

    /** The design's onShowMetrics: computes the metrics for the filters and fills the table. */
    void onShowMetrics(MetricsFilter filter) {
        gui.run("Computing performance metrics", () -> filter.model() == null ? gui.facade.metricsByModel(filter)
                : List.of(gui.facade.computeMetrics(filter)), list -> {
                    shown = list;
                    if (filter.model() == null) { // offer every model that has run, keeping the choice
                        var models = new java.util.ArrayList<>(List.of(ALL_MODELS));
                        list.stream().skip(1).forEach(m -> models.add(m.model()));
                        metricsModel.getItems().setAll(models);
                        metricsModel.setValue(ALL_MODELS);
                    }
                    var columns = new java.util.ArrayList<javafx.scene.control.TableColumn<List<String>, ?>>();
                    columns.add(Gui.column("Metric", (List<String> row) -> row.get(0), 180));
                    for (int i = 0; i < list.size(); i++) {
                        int c = i + 1;
                        String title = list.get(i).model() == null ? ALL_MODELS : list.get(i).model();
                        columns.add(Gui.column(title + " (" + list.get(i).sampleSize() + " runs)",
                                (List<String> row) -> row.get(c), 200));
                    }
                    metrics.getColumns().setAll(columns);
                    var rows = new java.util.ArrayList<List<String>>();
                    List<MetricRow> first = list.get(0).rows();
                    for (int r = 0; r < first.size(); r++) {
                        var row = new java.util.ArrayList<>(List.of(first.get(r).metric()));
                        for (PerformanceMetrics m : list) {
                            row.add(m.rows().get(r).display());
                        }
                        rows.add(row);
                    }
                    metrics.getItems().setAll(rows);
                    verdicts.setText(list.stream().map(m -> (m.model() == null ? ALL_MODELS : m.model()) + ": "
                            + m.truePositives() + " escalated and laundering, " + m.falsePositives()
                            + " escalated but not, " + m.falseNegatives() + " dismissed but laundering, "
                            + m.trueNegatives() + " dismissed and not, " + m.inconclusive() + " inconclusive")
                            .collect(Collectors.joining("\n")));
                });
    }

    private void exportMetrics() {
        if (shown.isEmpty()) {
            return;
        }
        var chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Export the performance metrics");
        chooser.setInitialFileName("tracewise-metrics.csv");
        var file = chooser.showSaveDialog(gui.stage);
        if (file == null) {
            return;
        }
        var list = shown;
        gui.run("Exporting the metrics", () -> {
            try (var w = java.nio.file.Files.newBufferedWriter(file.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                MetricsCsv.write(list, w);
            }
            return file;
        }, f -> gui.info("Metrics exported", "Written to " + f));
    }
}
