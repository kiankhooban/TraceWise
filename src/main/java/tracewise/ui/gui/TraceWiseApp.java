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

    @Override
    public void init() {
        config = AppConfig.fromEnvironment(System.getenv(), System.getProperty("user.name"));
        facade = TraceWiseBootstrap.create(config);
    }

    @Override
    public void start(Stage stage) {
        worker = new GuiWorker(Platform::runLater);
        var gui = new Gui(facade, worker, stage, config);
        var alerts = new AlertsView(gui);
        var caseView = new CaseView(gui);
        var approvals = new ApprovalsView(gui);
        var admin = new AdminView(gui);
        var assistant = new AssistantView(gui, caseView::currentCaseId);
        var alertsTab = new Tab("Alerts", alerts.build());
        var casesTab = new Tab("Cases", caseView.build());
        var approvalsTab = new Tab("Approvals", approvals.build());
        var adminTab = new Tab("Admin", admin.build());
        var tabs = new TabPane(alertsTab, casesTab, approvalsTab, adminTab);
        tabs.getSelectionModel().selectedItemProperty().addListener((o, old, tab) -> {
            if (tab == alertsTab) {
                alerts.refresh();
            } else if (tab == casesTab) {
                caseView.refresh();
            } else if (tab == approvalsTab) {
                approvals.refresh();
            } else {
                admin.refresh();
            }
        });
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
        root.setTop(statusBar());
        var scene = new Scene(root, 1280, 800);
        scene.getStylesheets().add(TraceWiseApp.class.getResource("/tracewise/gui.css").toExternalForm());
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

    private HBox statusBar() {
        var who = new Label(config.userName() + " (" + config.userRole() + ")   model " + config.model().modelName()
                + (config.model().provider().equals("replay") ? " (replay)" : "") + "   database "
                + config.databasePath().getFileName());
        var busy = new Label();
        var spinner = new ProgressIndicator();
        spinner.setPrefSize(16, 16);
        spinner.setVisible(false);
        worker.onStatus(s -> {
            busy.setText(s.isEmpty() ? "" : s + "...");
            spinner.setVisible(!s.isEmpty());
        });
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var bar = new HBox(8, new Label("TraceWise"), who, spacer, busy, spinner);
        bar.getStyleClass().add("status-bar");
        bar.setPadding(new Insets(6, 12, 6, 12));
        bar.getChildren().get(0).getStyleClass().add("brand");
        return bar;
    }

    @Override
    public void stop() throws Exception {
        worker.close();
        facade.close();
    }
}
