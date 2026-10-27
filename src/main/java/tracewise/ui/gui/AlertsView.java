package tracewise.ui.gui;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.application.Platform;
import tracewise.agent.AgentEventListener;
import tracewise.agent.AgentOutcome;
import tracewise.agent.TraceStep;
import tracewise.app.TriageLimits;
import tracewise.domain.Alert;
import tracewise.domain.AlertStatus;
import tracewise.domain.ThresholdReport;
import tracewise.ingest.ImportPreview;
import tracewise.ingest.ImportSummary;
import tracewise.monitoring.AlertFilter;
import tracewise.monitoring.MonitoringSummary;

/** F01, F02: import transactions, run monitoring, the alert queue and the Threshold Report Register. */
final class AlertsView {

    private static final String ALL = "All";

    private final Gui gui;
    private final TableView<Alert> alerts = new TableView<>();
    private final TableView<ThresholdReport> reports = new TableView<>();
    private final ComboBox<String> rule = new ComboBox<>(FXCollections.observableArrayList(ALL, "R1", "R2", "R3", "R4", "R5", "R6"));
    private final ComboBox<String> status = new ComboBox<>(FXCollections.observableArrayList(ALL, "NEW", "IN_CASE", "CLOSED"));
    private final TextField minTotal = new TextField();
    private final Label count = new Label();
    private final TextField maxAlerts = new TextField("50");
    private final TextField maxCost = new TextField("5.00");
    private final Button stop = new Button("Stop");
    private final javafx.scene.control.TextArea activity = new javafx.scene.control.TextArea();

    AlertsView(Gui gui) {
        this.gui = gui;
    }

    Node build() {
        rule.setValue(ALL);
        status.setValue(ALL);
        minTotal.setPromptText("min total CAD");
        minTotal.setPrefColumnCount(9);
        var apply = new Button("Apply");
        apply.setOnAction(e -> refresh());
        var importCsv = new Button("Import CSV...");
        importCsv.setOnAction(e -> onImport());
        var monitor = new Button("Run Monitoring");
        monitor.getStyleClass().add("primary");
        monitor.setOnAction(e -> onRunMonitoring());
        var investigate = new Button("Investigate");
        investigate.setOnAction(e -> onInvestigate());
        maxAlerts.setPrefColumnCount(3);
        maxCost.setPrefColumnCount(5);
        var triage = new Button("Triage Queue");
        triage.getStyleClass().add("primary");
        triage.setOnAction(e -> onTriage());
        stop.setDisable(true);
        stop.setOnAction(e -> gui.facade.stopTriage()); // only sets a flag: safe on the UI thread
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var toolbar = new HBox(8, importCsv, monitor, investigate, new Label("  Triage: max alerts"), maxAlerts,
                new Label("max CAD"), maxCost, triage, stop, spacer, new Label("Rule"), rule, new Label("Status"), status,
                minTotal, apply);
        toolbar.getStyleClass().add("toolbar");
        activity.setEditable(false);
        activity.setPrefRowCount(7);
        activity.setPromptText("Agent activity during triage appears here.");
        alerts.setRowFactory(t -> {
            var row = new javafx.scene.control.TableRow<Alert>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    open(row.getItem());
                }
            });
            return row;
        });

        alerts.getColumns().setAll(List.of(Gui.column("Alert", Alert::id, 95), Gui.column("Rule", Alert::ruleId, 50),
                Gui.column("Account", Alert::accountId, 90), Gui.column("Total CAD", Alert::totalCad, 110),
                Gui.column("Risk", Alert::riskScore, 50), Gui.column("Priority", Alert::priority, 60),
                Gui.column("Status", Alert::status, 80), Gui.column("Flagged", a -> a.transactionIds().size(), 60),
                Gui.column("Created (UTC)", Alert::createdAt, 130)));
        alerts.setPlaceholder(new Label("No alerts. Import transactions and run monitoring."));
        reports.getColumns().setAll(List.of(Gui.column("Report", ThresholdReport::id, 95),
                Gui.column("Rule", ThresholdReport::ruleId, 50), Gui.column("Account", ThresholdReport::accountId, 90),
                Gui.column("Total CAD", ThresholdReport::totalCad, 110),
                Gui.column("Transactions", r -> r.transactionIds().size(), 90),
                Gui.column("Window start (UTC)", ThresholdReport::windowStart, 140),
                Gui.column("Window end (UTC)", ThresholdReport::windowEnd, 140)));
        reports.setPlaceholder(new Label("No threshold reports yet."));

        var queue = new BorderPane(alerts);
        queue.setBottom(count);
        BorderPane.setMargin(count, new Insets(6, 0, 0, 0));
        var tabs = new TabPane(new Tab("Alert queue", queue), new Tab("Threshold Report Register", reports));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        var root = new BorderPane(new javafx.scene.control.SplitPane(tabs, activity));
        ((javafx.scene.control.SplitPane) root.getCenter()).setOrientation(javafx.geometry.Orientation.VERTICAL);
        ((javafx.scene.control.SplitPane) root.getCenter()).setDividerPositions(0.78);
        root.setTop(toolbar);
        root.setPadding(new Insets(10));
        return root;
    }

    void refresh() {
        AlertFilter filter;
        try {
            filter = new AlertFilter(ALL.equals(rule.getValue()) ? null : rule.getValue(),
                    ALL.equals(status.getValue()) ? null : AlertStatus.valueOf(status.getValue()),
                    minTotal.getText().isBlank() ? null : new BigDecimal(minTotal.getText().strip()));
        } catch (NumberFormatException e) {
            gui.info("Invalid filter", "The minimum total must be a number, for example 10000.");
            return;
        }
        gui.run("Loading alerts", () -> new Object[] {gui.facade.listAlerts(filter), gui.facade.listThresholdReports()}, r -> {
            @SuppressWarnings("unchecked")
            List<Alert> a = (List<Alert>) r[0];
            @SuppressWarnings("unchecked")
            List<ThresholdReport> t = (List<ThresholdReport>) r[1];
            alerts.getItems().setAll(a);
            reports.getItems().setAll(t);
            count.setText(a.size() + " alerts, highest risk first");
        });
    }

    private void onImport() {
        var chooser = new FileChooser();
        chooser.setTitle("Import transactions");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        var file = chooser.showOpenDialog(gui.stage);
        if (file == null) {
            return;
        }
        Path path = file.toPath();
        gui.run("Reading " + file.getName(), () -> gui.facade.previewImport(path), preview -> {
            if (gui.confirm("Import " + file.getName() + "?", describe(preview))) {
                gui.run("Importing " + file.getName(), () -> gui.facade.importTransactions(path), summary -> {
                    gui.info("Import finished", describe(summary));
                    refresh();
                });
            }
        });
    }

    /** Double-click: an alert in a case opens it; a new alert asks whether to investigate. */
    private void open(Alert a) {
        if (a.status() == AlertStatus.NEW) {
            if (gui.confirm("Investigate " + a.id() + "?", "The agent investigates the alert and proposes actions; "
                    + "closing or escalating needs your approval.")) {
                gui.investigate.accept(a.id());
            }
        } else {
            gui.openCase.accept(caseOf(a));
        }
    }

    private void onInvestigate() {
        Alert a = alerts.getSelectionModel().getSelectedItem();
        if (a == null) {
            gui.info("Select an alert", "Select the alert to investigate in the queue.");
        } else {
            gui.investigate.accept(a.id());
        }
    }

    /** A case is named after the alert that opened it; a linked alert is found through the Cases screen. */
    private static String caseOf(Alert a) {
        return "CAS-" + a.id().substring(4);
    }

    private void onTriage() {
        TriageLimits limits;
        try {
            limits = new TriageLimits(Integer.parseInt(maxAlerts.getText().strip()), new BigDecimal(maxCost.getText().strip()),
                    AlertFilter.ALL);
        } catch (NumberFormatException e) {
            gui.info("Invalid limits", "Max alerts must be a whole number and max CAD a number, for example 50 and 5.00.");
            return;
        }
        activity.clear();
        stop.setDisable(false);
        var listener = new AgentEventListener() {
            @Override
            public void onStep(TraceStep s) {
                String line = "[" + s.subject() + "] turn " + s.turn() + "  " + s.toolName() + (s.error() ? "  ERROR" : "") + "\n";
                Platform.runLater(() -> activity.appendText(line));
            }

            @Override
            public void onFinished(AgentOutcome o) {
                Platform.runLater(() -> activity.appendText("[" + o.subject() + "] " + o.status() + "\n"));
            }
        };
        gui.worker.submit("Triaging the alert queue", () -> gui.facade.triage(limits, listener), report -> {
            stop.setDisable(true);
            gui.info("Triage " + report.endReason(), report.summary() + "\n\n" + report.items().stream()
                    .map(i -> i.alertId() + "  " + i.caseId() + "  " + i.status() + (i.verdict() == null ? "" : "  " + i.verdict())
                            + (i.verification() == null ? "" : "  (" + i.verification() + ")"))
                    .collect(Collectors.joining("\n")));
            refresh();
        }, e -> {
            stop.setDisable(true);
            gui.error(e);
        });
    }

    private void onRunMonitoring() {
        gui.run("Running monitoring rules", gui.facade::runMonitoring, summary -> {
            gui.info("Monitoring finished", describe(summary));
            refresh();
        });
    }

    private static String describe(ImportPreview p) {
        return "Format: " + p.formatName() + "\nColumns: " + String.join(", ", p.header()) + "\n\nFirst rows:\n"
                + p.firstRows().stream().map(r -> String.join(", ", r)).collect(Collectors.joining("\n"));
    }

    static String describe(ImportSummary s) {
        String text = s.rowsImported() + " of " + s.rowsRead() + " rows imported, " + s.accounts() + " accounts.\n"
                + "Dates: " + Gui.text(s.earliest()) + " to " + Gui.text(s.latest()) + " (UTC)\n"
                + s.rejected().size() + " rows rejected";
        if (!s.rejected().isEmpty()) {
            text += ":\n" + s.rejected().stream().limit(20).map(r -> "  row " + r.rowNumber() + ": " + r.reason())
                    .collect(Collectors.joining("\n")) + (s.rejected().size() > 20 ? "\n  ..." : "");
        }
        return text;
    }

    private static String describe(MonitoringSummary s) {
        return s.thresholdReports() + " threshold reports, " + s.newAlerts() + " new alerts"
                + (s.alertsByRule().isEmpty() ? "" : ": " + s.alertsByRule())
                + (s.failedRules().isEmpty() ? "" : "\nRules that failed: " + String.join(", ", s.failedRules()));
    }
}
