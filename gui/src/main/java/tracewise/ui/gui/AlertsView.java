package tracewise.ui.gui;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
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
    private static final java.time.format.DateTimeFormatter LOG_TIME =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss").withZone(java.time.ZoneId.systemDefault());

    private final Gui gui;
    private final TableView<Alert> alerts = new TableView<>();
    private final TableView<ThresholdReport> reports = new TableView<>();
    private final ComboBox<String> rule = new ComboBox<>(FXCollections.observableArrayList(ALL, "R1", "R2", "R3", "R4", "R5", "R6"));
    private final ComboBox<String> status = new ComboBox<>(FXCollections.observableArrayList(ALL,
            Display.label(AlertStatus.NEW), Display.label(AlertStatus.IN_CASE), Display.label(AlertStatus.CLOSED)));
    private final TextField minTotal = new TextField();
    private final Label count = new Label();
    private final TextField maxAlerts = new TextField("50");
    private final TextField maxCost = new TextField("5.00");
    private final Button stop = new Button("Stop");
    private final javafx.scene.control.TextArea activity = new javafx.scene.control.TextArea();

    /** An alert id typed in the command bar, selected once the list has loaded. */
    private String pendingSelect;

    AlertsView(Gui gui) {
        this.gui = gui;
    }

    Node build() {
        rule.setValue(ALL);
        status.setValue(ALL);
        minTotal.setPromptText("min total CAD");
        minTotal.setPrefColumnCount(10);
        minTotal.getStyleClass().add("mono");
        maxAlerts.setPrefColumnCount(3);
        maxCost.setPrefColumnCount(4);
        maxAlerts.getStyleClass().addAll("mono", "num");
        maxCost.getStyleClass().addAll("mono", "num");
        var triage = Gui.button("Start triage  F3", this::startTriage);
        triage.getStyleClass().add("primary");
        stop.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        stop.setDisable(true);
        stop.setOnAction(e -> gui.facade.stopTriage()); // only sets a flag: safe on the UI thread
        // the filters wrap as one group, so a label never ends a line apart from its control
        var filters = new HBox(6, Gui.label("Rule", "muted"), rule, Gui.label("Status", "muted"), status, minTotal,
                Gui.button("Apply", this::refresh));
        filters.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        var toolbar = new javafx.scene.layout.FlowPane(6, 6, Gui.button("Import CSV", this::onImport),
                Gui.button("Run monitoring", this::onRunMonitoring), Gui.button("Investigate  F2", this::investigateSelected),
                sep(), Gui.label("Triage up to", "muted"), maxAlerts, Gui.label("alerts or", "muted"), maxCost,
                Gui.label("CAD", "muted"), triage, stop, sep(), filters);
        toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("toolbar");
        activity.setEditable(false);
        activity.setWrapText(false);
        activity.getStyleClass().addAll("mono", "prose-box");
        activity.setPromptText("Agent steps appear here while triage runs: time, alert, tool, result.");
        alerts.setRowFactory(t -> {
            var row = new javafx.scene.control.TableRow<Alert>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    open(row.getItem());
                }
            });
            return row;
        });

        alerts.getColumns().setAll(List.of(
                age(),
                Gui.column("Alert", Alert::id, 96, "mono"),
                Gui.column("Rule", a -> a.ruleId() + "  " + Display.ruleName(a.ruleId()), 170),
                Gui.column("Account", Alert::accountId, 80, "mono"),
                Gui.column("Total CAD", Alert::totalCad, 110, "mono", "num"),
                risk(),
                Gui.column("Pri", Alert::priority, 44, "mono", "num"),
                Gui.column("Status", a -> Display.label(a.status()), 90),
                Gui.column("Tx", a -> a.transactionIds().size(), 44, "mono", "num"),
                Gui.column("Created UTC", Alert::createdAt, 146, "mono", "muted")));
        alerts.setPlaceholder(new Label("No alerts. Import transactions (Import CSV), then run monitoring."));
        reports.getColumns().setAll(List.of(Gui.column("Report", ThresholdReport::id, 96, "mono"),
                Gui.column("Rule", r -> r.ruleId() + "  " + Display.ruleName(r.ruleId()), 200),
                Gui.column("Account", ThresholdReport::accountId, 80, "mono"),
                Gui.column("Total CAD", ThresholdReport::totalCad, 110, "mono", "num"),
                Gui.column("Tx", r -> r.transactionIds().size(), 44, "mono", "num"),
                Gui.column("Window start UTC", ThresholdReport::windowStart, 140, "mono"),
                Gui.column("Window end UTC", ThresholdReport::windowEnd, 140, "mono")));
        reports.setPlaceholder(new Label("No threshold reports yet. Run monitoring after an import."));

        count.getStyleClass().add("muted");
        var tabs = new TabPane(new Tab("Alert queue", alerts), new Tab("Threshold Report Register", reports));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        var queue = new javafx.scene.layout.VBox(Gui.panelHead("ALRT", "Alert queue", count), toolbar, tabs);
        javafx.scene.layout.VBox.setVgrow(tabs, Priority.ALWAYS);
        var log = new BorderPane(activity);
        log.setTop(Gui.panelHead("LOG", "Agent activity"));
        var split = new javafx.scene.control.SplitPane(queue, log);
        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.setDividerPositions(0.72);
        return split;
    }

    private static Region sep() {
        var r = new Region();
        r.getStyleClass().add("separator-v");
        return r;
    }

    void refresh() {
        AlertFilter filter;
        try {
            filter = new AlertFilter(ALL.equals(rule.getValue()) ? null : rule.getValue(),
                    Display.parse(AlertStatus.class, status.getValue()),
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
            if (pendingSelect != null) {
                String id = pendingSelect;
                pendingSelect = null;
                a.stream().filter(x -> x.id().equals(id)).findFirst().ifPresentOrElse(x -> {
                    alerts.getSelectionModel().select(x);
                    alerts.scrollTo(x);
                }, () -> gui.info(id + " is not listed", "Check the id, or clear the filters (Rule, Status, min total)."));
            }
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
            gui.run("Finding the case of " + a.id(), () -> gui.facade.caseOfAlert(a.id()),
                    id -> id.ifPresentOrElse(gui.openCase, () -> gui.info("No case", a.id() + " is not in a case.")));
        }
    }

    void investigateSelected() {
        Alert a = alerts.getSelectionModel().getSelectedItem();
        if (a == null) {
            gui.info("Select an alert", "Select the alert to investigate in the queue.");
        } else {
            gui.investigate.accept(a.id());
        }
    }

    void startTriage() {
        if (!stop.isDisable()) { // Stop is enabled exactly while a triage runs: a held F3 must not queue paid runs
            gui.info("Triage is running", "Wait for it to finish, or press Stop, first.");
            return;
        }
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
                String line = logLine(s.subject(), s.toolName(), s.error() ? "ERROR" : "ok");
                Platform.runLater(() -> activity.appendText(line));
            }

            @Override
            public void onFinished(AgentOutcome o) {
                String line = logLine(o.subject(), "finished", String.valueOf(o.status()));
                Platform.runLater(() -> activity.appendText(line));
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
                + s.rejectedCount() + " rows rejected";
        if (!s.rejected().isEmpty()) {
            text += ":\n" + s.rejected().stream().limit(20).map(r -> "  row " + r.rowNumber() + ": " + r.reason())
                    .collect(Collectors.joining("\n")) + (s.rejectedCount() > 20 ? "\n  ... by reason: " + s.rejectedByReason() : "");
        }
        return text;
    }

    private static String describe(MonitoringSummary s) {
        return s.thresholdReports() + " threshold reports, " + s.newAlerts() + " new alerts"
                + (s.alertsByRule().isEmpty() ? "" : ": " + s.alertsByRule())
                + (s.failedRules().isEmpty() ? "" : "\nRules that failed: " + String.join(", ", s.failedRules()));
    }

    /** The account of the selected alert, or null: the assistant's context on this screen. */
    String selectedAccount() {
        Alert a = alerts.getSelectionModel().getSelectedItem();
        return a == null ? null : a.accountId();
    }

    /** "HIGH 65": the level for the eye, sorted by the score. */
    private static javafx.scene.control.TableColumn<Alert, String> risk() {
        var c = Gui.styled("Risk", (Alert a) -> Display.riskLevel(a.riskScore()) + " " + a.riskScore(),
                a -> Display.riskStyle(a.riskScore()), 84, "mono");
        c.setComparator(Display.BY_NUMBER);
        return c;
    }

    /** Time since the alert was raised; not sortable as text ("12m" vs "2h 05m"): Created UTC sorts the same order. */
    private static javafx.scene.control.TableColumn<Alert, String> age() {
        var c = Gui.column("Age", (Alert a) -> Display.age(java.time.Duration.between(a.createdAt(), java.time.Instant.now())),
                64, "mono", "muted");
        c.setSortable(false);
        return c;
    }

    /** Command bar: select an alert by id (after a reload, so a new alert is there too). */
    void select(String alertId) {
        pendingSelect = alertId;
        refresh();
    }

    private static String logLine(String subject, String what, String result) {
        return String.format("%s  %-10s %-20s %s%n", LOG_TIME.format(java.time.Instant.now()), subject, what, result);
    }
}
