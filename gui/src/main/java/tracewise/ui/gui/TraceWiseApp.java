package tracewise.ui.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import tracewise.app.AppConfig;
import tracewise.app.TraceWiseBootstrap;
import tracewise.app.TraceWiseFacade;

/** The JavaFX front end. Like the CLI it talks to the core only through TraceWiseFacade. */
public class TraceWiseApp extends Application {

    private AppConfig config;
    private TraceWiseFacade facade;
    private GuiWorker worker;
    private CaseView caseView;

    @Override
    public void init() {
        config = AppConfig.fromEnvironment(System.getenv(), System.getProperty("user.name"));
        facade = TraceWiseBootstrap.create(config);
    }

    @Override
    public void start(Stage stage) {
        Theme.loadFonts();
        worker = new GuiWorker(Platform::runLater);
        var gui = new Gui(facade, worker, stage, config);
        var alerts = new AlertsView(gui);
        caseView = new CaseView(gui);
        var approvals = new ApprovalsView(gui);
        var admin = new AdminView(gui);
        var alertsTab = screen("1", "Alerts", alerts.build());
        var casesTab = screen("2", "Cases", caseView.build());
        var approvalsTab = screen("3", "Approvals", approvals.build());
        var adminTab = screen("4", "Admin", admin.build());
        var tabs = new TabPane(alertsTab, casesTab, approvalsTab, adminTab);
        tabs.getStyleClass().add("screens");
        // the assistant's context is what the current screen has selected (Stage 1 F10: "the selected case or account")
        var assistant = new AssistantView(gui, () -> {
            var t = tabs.getSelectionModel().getSelectedItem();
            if (t == alertsTab) {
                return new tracewise.agent.AssistantContext(null, alerts.selectedAccount());
            }
            if (t == casesTab) {
                return new tracewise.agent.AssistantContext(caseView.currentCaseId(), null);
            }
            return t == approvalsTab ? new tracewise.agent.AssistantContext(approvals.selectedCaseId(), null)
                    : tracewise.agent.AssistantContext.NONE;
        });
        // one place says how each screen reloads: on switching to it and on F5
        java.util.function.Consumer<Tab> refreshScreen = tab -> {
            if (tab == alertsTab) {
                alerts.refresh();
            } else if (tab == casesTab) {
                caseView.refresh();
            } else if (tab == approvalsTab) {
                approvals.refresh();
            } else {
                admin.refresh();
            }
        };
        tabs.getSelectionModel().selectedItemProperty().addListener((o, old, tab) -> refreshScreen.accept(tab));
        gui.openCase = id -> {
            tabs.getSelectionModel().select(casesTab);
            caseView.showCase(id);
        };
        gui.investigate = id -> {
            tabs.getSelectionModel().select(casesTab);
            caseView.investigate(id);
        };
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        var split = new javafx.scene.control.SplitPane(tabs, assistant.build());
        split.setDividerPositions(0.74);
        var root = new BorderPane(split);
        var command = new javafx.scene.control.TextField();
        command.setPromptText("Command or id: ALRT, CASE CAS-00001, APRV, ADMN, ASK <question>   " + MOD + "+K");
        command.getStyleClass().add("command");
        command.setOnAction(e -> {
            var c = Command.parse(command.getText());
            if (c == null) {
                return;
            }
            switch (c.target()) {
                case ALERTS -> {
                    tabs.getSelectionModel().select(alertsTab);
                    if (!c.argument().isEmpty()) {
                        alerts.select(c.argument());
                    }
                }
                case CASES -> {
                    if (c.argument().isEmpty()) {
                        tabs.getSelectionModel().select(casesTab);
                    } else {
                        gui.openCase.accept(c.argument());
                    }
                }
                case APPROVALS -> {
                    tabs.getSelectionModel().select(approvalsTab);
                    if (!c.argument().isEmpty()) {
                        approvals.select(c.argument());
                    }
                }
                case ADMIN -> tabs.getSelectionModel().select(adminTab);
                case ASK -> assistant.onSubmit(c.argument());
                case UNKNOWN -> gui.info("Unknown command", "Try ALRT, CASE CAS-00001, APRV, ADMN or ASK followed by a question.");
            }
            command.clear();
        });
        root.setTop(topBar(command));
        boolean[] day = {Theme.day()};
        root.setBottom(keyBar(() -> {
            day[0] = !day[0];
            Theme.remember(day[0]);
            Theme.apply(root.getScene(), day[0]);
        }));
        var scene = new Scene(root, 1280, 800);
        Theme.apply(scene, day[0]);
        var keys = scene.getAccelerators();
        var tabList = java.util.List.of(alertsTab, casesTab, approvalsTab, adminTab);
        for (int i = 0; i < tabList.size(); i++) {
            Tab t = tabList.get(i);
            keys.put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.valueOf("DIGIT" + (i + 1)),
                    javafx.scene.input.KeyCombination.SHORTCUT_DOWN), () -> tabs.getSelectionModel().select(t));
        }
        keys.put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.K,
                javafx.scene.input.KeyCombination.SHORTCUT_DOWN), command::requestFocus);
        keys.put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F2), () -> {
            if (tabs.getSelectionModel().getSelectedItem() == alertsTab) {
                alerts.investigateSelected();
            }
        });
        keys.put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F3), () -> {
            if (tabs.getSelectionModel().getSelectedItem() == alertsTab) {
                alerts.startTriage();
            }
        });
        keys.put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F9), () -> {
            if (tabs.getSelectionModel().getSelectedItem() == approvalsTab) {
                approvals.approveSelected();
            }
        });
        keys.put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F10), () -> {
            if (tabs.getSelectionModel().getSelectedItem() == approvalsTab) {
                approvals.rejectSelected();
            }
        });
        keys.put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F5),
                () -> refreshScreen.accept(tabs.getSelectionModel().getSelectedItem()));
        stage.setTitle("TraceWise");
        stage.setScene(scene);
        stage.show();
        alerts.refresh();
        caseView.refresh();
        String snapshot = System.getenv("TRACEWISE_SNAPSHOT");
        if (snapshot != null) {
            // optional scripted steps through the same code as the buttons, for checking and report screenshots
            String investigate = System.getenv("TRACEWISE_SNAPSHOT_INVESTIGATE");
            if (investigate != null) {
                gui.investigate.accept(investigate);
            }
            String ask = System.getenv("TRACEWISE_SNAPSHOT_ASK");
            if (ask != null) {
                assistant.onSubmit(ask);
            }
            // queued after the loads above, so the screens show data when captured
            worker.submit("Saving screenshots", () -> null,
                    r -> Snapshots.ofTabs(scene, tabs, java.nio.file.Path.of(snapshot), Platform::exit), gui::error);
        }
    }

    /** SHORTCUT_DOWN is Cmd on macOS and Ctrl elsewhere; the legend names the key the user presses. */
    private static final String MOD = System.getProperty("os.name", "").startsWith("Mac") ? "Cmd" : "Ctrl";

    /** A screen tab with its keyboard number (Ctrl or Cmd + 1 to 4). */
    private static Tab screen(String key, String name, javafx.scene.Node content) {
        var tab = new Tab(name, content);
        tab.setGraphic(Gui.label(key, "panel-code"));
        return tab;
    }

    private HBox topBar(javafx.scene.control.TextField command) {
        var wordmark = Gui.label("TRACEWISE", "wordmark");
        var mark = Gui.label(">", "command-mark");
        HBox.setHgrow(command, Priority.ALWAYS);
        var identity = Gui.label(config.userRole() + " " + config.userName() + "   MODEL " + config.model().runLabel()
                + (config.model().costKnown() ? "" : " (no prices known: cost limits do not apply)") + "   DB "
                + config.databasePath().getFileName(), "identity");
        identity.setPadding(new Insets(0, 14, 0, 14));
        var bar = new HBox(wordmark, mark, command, identity);
        bar.getStyleClass().add("top-bar");
        return bar;
    }

    /** The key legend, what the app is busy with, and the day or night switch. */
    private HBox keyBar(Runnable toggleTheme) {
        var bar = new HBox();
        for (String[] k : new String[][] {{MOD + "+1-4", "Screens"}, {MOD + "+K", "Command"}, {"F2", "Investigate"},
                {"F3", "Triage"}, {"F9", "Approve"}, {"F10", "Reject"}, {"F5", "Refresh"}}) {
            var key = Gui.label(k[0], "key");
            bar.getChildren().add(new HBox(6, key, new Label(k[1])));
        }
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var busy = new Label();
        var spinner = new ProgressIndicator();
        spinner.setPrefSize(14, 14);
        spinner.setVisible(false);
        worker.onStatus(s -> {
            busy.setText(s.isEmpty() ? "" : s + "...");
            spinner.setVisible(!s.isEmpty());
        });
        var theme = new Label("Day / Night");
        theme.getStyleClass().add("key");
        theme.setOnMouseClicked(e -> toggleTheme.run());
        theme.setCursor(javafx.scene.Cursor.HAND);
        bar.getChildren().addAll(spacer, busy, spinner, theme);
        bar.getStyleClass().add("key-bar");
        return bar;
    }

    /** Asks running agent work to stop, waits for the worker, and closes the database only once nothing uses it. */
    @Override
    public void stop() throws Exception {
        facade.stopTriage();
        if (caseView.runningAlert() != null) {
            facade.cancelInvestigation(caseView.runningAlert());
        }
        if (worker.shutdown()) {
            facade.close();
        }
    }
}
