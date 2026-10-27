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
import tracewise.monitoring.DetectionRule;
import tracewise.monitoring.RuleConfig;
import tracewise.retrieval.RetrievedPassage;

/** Rules (F02, supervisors change them), knowledge search (F04) and the audit log (F08). */
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

    AdminView(Gui gui) {
        this.gui = gui;
    }

    Node build() {
        var tabs = new TabPane(new Tab("Rules", rulesPane()), new Tab("Knowledge", knowledgePane()),
                new Tab("Audit Log", auditPane()));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        var root = new BorderPane(tabs);
        root.setPadding(new Insets(10));
        return root;
    }

    void refresh() {
        gui.run("Loading rules", gui.facade::listRules, list -> rules.getItems().setAll(list));
        searchAudit();
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
}
