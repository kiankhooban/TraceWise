package tracewise.ui.gui;

import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import tracewise.actions.PendingAction;
import tracewise.agent.AgentFinding;

/** F06: actions the agent proposed that wait for a human decision, each with the finding behind it. */
final class ApprovalsView {

    private final Gui gui;
    private final TableView<PendingAction> pending = new TableView<>();
    private final TextArea details = new TextArea();

    ApprovalsView(Gui gui) {
        this.gui = gui;
    }

    Node build() {
        pending.getColumns().setAll(List.of(Gui.column("Proposal", PendingAction::id, 95),
                Gui.column("Action", p -> p.command().describe(), 360),
                Gui.column("Case", p -> p.command().targetCaseId().orElse(""), 90),
                Gui.column("Proposed by", p -> p.proposedBy().name(), 100),
                Gui.column("At (UTC)", PendingAction::proposedAt, 130)));
        pending.setPlaceholder(new Label("No proposals waiting."));
        pending.getSelectionModel().selectedItemProperty().addListener((o, old, p) -> describe(p));
        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefRowCount(9);
        var approve = new Button("Approve");
        approve.getStyleClass().add("primary");
        approve.setOnAction(e -> decide(true));
        var reject = new Button("Reject...");
        reject.setOnAction(e -> decide(false));
        var openCase = new Button("Open case");
        openCase.setOnAction(e -> selected().flatMap(p -> p.command().targetCaseId()).ifPresent(gui.openCase));
        var refresh = new Button("Refresh");
        refresh.setOnAction(e -> refresh());
        var bar = new HBox(8, approve, reject, openCase, refresh);
        bar.getStyleClass().add("toolbar");
        var note = new Label("The agent can only propose closing or escalating a case. Approving runs the action; "
                + "if the case changed since, the proposal expires instead.");
        note.getStyleClass().add("muted");
        var root = new BorderPane(pending);
        root.setTop(new VBox(6, bar, note));
        root.setBottom(details);
        BorderPane.setMargin(details, new Insets(8, 0, 0, 0));
        root.setPadding(new Insets(10));
        return root;
    }

    void refresh() {
        gui.run("Loading proposals", gui.facade::pendingApprovals, list -> {
            pending.getItems().setAll(list);
            if (!list.isEmpty()) {
                pending.getSelectionModel().selectFirst();
            } else {
                details.clear();
            }
        });
    }

    private java.util.Optional<PendingAction> selected() {
        return java.util.Optional.ofNullable(pending.getSelectionModel().getSelectedItem());
    }

    private void describe(PendingAction p) {
        if (p == null) {
            details.clear();
            return;
        }
        String head = p.command().describe() + "\nWhy: " + p.justification() + "\n";
        gui.run("Loading the finding", () -> p.command().targetCaseId().map(id -> gui.facade.getCase(id).latestFinding()),
                f -> details.setText(head + f.map(ApprovalsView::finding).orElse("\nNo finding on the case.")));
    }

    private static String finding(AgentFinding f) {
        return "\nFinding: " + f.verdict() + ", confidence " + f.confidence() + "\nVerification: "
                + f.verification().status() + ", " + f.verification().summary() + "\n\n" + f.narrative();
    }

    private void decide(boolean approve) {
        var p = selected().orElse(null);
        if (p == null) {
            gui.info("Select a proposal", "Select the proposal to decide on.");
            return;
        }
        if (approve) {
            if (gui.confirm("Approve " + p.id() + "?", p.command().describe())) {
                gui.run("Approving " + p.id(), () -> gui.facade.approve(p.id()), this::decided);
            }
            return;
        }
        var ask = new TextInputDialog();
        ask.initOwner(gui.stage);
        ask.setTitle("Reject " + p.id());
        ask.setHeaderText("Why is it rejected? The agent sees the reason if the case is investigated again.");
        ask.showAndWait().filter(r -> !r.isBlank())
                .ifPresent(reason -> gui.run("Rejecting " + p.id(), () -> gui.facade.reject(p.id(), reason), this::decided));
    }

    private void decided(PendingAction p) {
        gui.info(p.id() + " " + p.status(), p.command().describe()
                + (p.decisionReason() == null ? "" : "\nReason: " + p.decisionReason()));
        refresh();
    }
}
