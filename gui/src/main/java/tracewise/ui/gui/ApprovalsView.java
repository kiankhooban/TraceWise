package tracewise.ui.gui;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import tracewise.actions.PendingAction;
import tracewise.domain.Alert;
import tracewise.domain.Case;
import tracewise.monitoring.AlertFilter;

/**
 * F06: actions the agent proposed that wait for a human decision. The agent proposes, a person decides, and both are
 * shown and logged as AGENT and HUMAN (docs/stage2/ui-design.md).
 */
final class ApprovalsView {

    private final Gui gui;
    private final TableView<ProposalRow> pending = new TableView<>();
    private final Label count = Gui.label("", "muted", "mono");
    private final Label title = Gui.label("Select a proposal", "section-title", "mono");
    private final Label reason = Gui.label("", "prose");
    private final TableView<CaseFacts.Flow> flows = new TableView<>();
    private final Label net = Gui.label("", "mono", "soft");
    private final GridPane decision = new GridPane();
    private final TextField rejectReason = new TextField();
    private final Label last = Gui.label("", "mono", "muted");
    private Map<String, Alert> alerts = Map.of();
    /** A proposal id typed in the command bar, selected once the queue has loaded. */
    private String pendingSelect;

    ApprovalsView(Gui gui) {
        this.gui = gui;
    }

    Node build() {
        pending.getColumns().setAll(List.of(Gui.column("Proposal", r -> r.proposal().id(), 96, "mono"),
                Gui.column("Action", r -> r.proposal().command().describe(), 330),
                Gui.column("Case", ProposalRow::caseId, 92, "mono"),
                Gui.column("Account", ProposalRow::account, 76, "mono"),
                Gui.styled("Finding", ProposalRow::finding, ProposalRow::findingStyle, 118, "mono"),
                Gui.styled("Checked", ProposalRow::checked, ProposalRow::checkedStyle, 96, "mono"),
                Gui.column("Proposed by", ProposalRow::proposedBy, 92, "mono"),
                Gui.column("Waiting", ProposalRow::waiting, 70, "mono", "num")));
        pending.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        pending.getColumns().get(pending.getColumns().size() - 1).setSortable(false); // Waiting is an age, not a number
        pending.setPlaceholder(new Label("No proposals waiting. The agent proposes closing or escalating a case "
                + "when it finishes an investigation."));
        pending.getSelectionModel().selectedItemProperty().addListener((o, old, r) -> describe(r));
        pending.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                openSelected();
            }
        });
        var note = Gui.label("The agent can only propose closing or escalating a case. A person decides; if the case "
                + "changed since, the proposal expires instead.", "muted");
        note.setWrapText(true);
        var top = new VBox(Gui.panelHead("APRV", "Approval queue", count), note);
        VBox.setMargin(note, new Insets(6, 12, 6, 12));

        flows.getColumns().setAll(List.of(Gui.styled("", CaseFacts.Flow::direction, f -> "In".equals(f.direction()) ? "ok" : "wait", 44, "mono"),
                Gui.column("Transaction", CaseFacts.Flow::id, 104, "mono"),
                Gui.column("CAD", CaseFacts.Flow::amount, 110, "mono", "num"),
                Gui.column("Counterparty", CaseFacts.Flow::counterparty, 110, "mono"),
                Gui.column("UTC", CaseFacts.Flow::time, 100, "mono", "muted")));
        flows.setPlaceholder(new Label("No flagged transactions loaded."));
        flows.setPrefHeight(26 * 5);
        flows.setMaxWidth(480);
        flows.setMinWidth(480);
        reason.setWrapText(true);
        reason.setMaxWidth(720);
        var evidence = new VBox(8, title, section("The agent's reason"), reason, section("Flagged money"), flows, net);
        evidence.setPadding(new Insets(12, 14, 12, 14));
        var evidenceScroll = new ScrollPane(evidence);
        evidenceScroll.setFitToWidth(true);
        HBox.setHgrow(evidenceScroll, Priority.ALWAYS);

        decision.setHgap(12);
        decision.setVgap(7);
        rejectReason.setPromptText("Reason, required to reject");
        var approve = Gui.button("Approve  F9", this::approveSelected);
        approve.getStyleClass().add("primary");
        var reject = Gui.button("Reject  F10", this::rejectSelected);
        var open = Gui.button("Open case  Enter", this::openSelected);
        var buttons = new HBox(8, approve, reject, open);
        var decide = new VBox(10, decision, rejectReason, buttons, last);
        decide.setPadding(new Insets(12));
        var decidePanel = new VBox(Gui.panelHead("DCSN", "Decision"), decide);
        decidePanel.getStyleClass().add("side-panel");
        decidePanel.setMinWidth(360);

        decidePanel.setPrefWidth(400);
        var detail = new HBox(evidenceScroll, decidePanel);
        var split = new SplitPane(pending, detail);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.36);
        var root = new BorderPane(split);
        root.setTop(top);
        return root;
    }

    void refresh() {
        Instant now = Instant.now();
        gui.run("Loading proposals", () -> {
            var byId = gui.facade.listAlerts(AlertFilter.ALL).stream().collect(Collectors.toMap(Alert::id, Function.identity()));
            var rows = new ArrayList<ProposalRow>();
            for (PendingAction p : gui.facade.pendingApprovals()) {
                Case c = p.command().targetCaseId().map(this::caseOrNull).orElse(null);
                Alert a = c == null ? null : byId.get(c.openingAlertId());
                rows.add(new ProposalRow(p, c, a == null ? "" : a.accountId(), now));
            }
            return new Loaded(byId, rows);
        }, loaded -> {
            alerts = loaded.alerts();
            pending.getItems().setAll(loaded.rows());
            count.setText(loaded.rows().size() + " waiting");
            var want = loaded.rows().stream().filter(r -> r.proposal().id().equals(pendingSelect)).findFirst();
            if (pendingSelect != null && want.isEmpty()) {
                gui.info(pendingSelect + " is not waiting", "Only proposals waiting for a decision are listed.");
            }
            pendingSelect = null;
            if (want.isPresent()) {
                pending.getSelectionModel().select(want.get());
            } else if (loaded.rows().isEmpty()) {
                describe(null);
            } else {
                pending.getSelectionModel().selectFirst();
            }
        });
    }

    private record Loaded(Map<String, Alert> alerts, List<ProposalRow> rows) {
    }

    /** A proposal whose case no longer exists still shows, without its finding; any other read error is shown. */
    private Case caseOrNull(String id) {
        try {
            return gui.facade.getCase(id);
        } catch (IllegalArgumentException e) { // getCase's "No case <id>"
            return null;
        }
    }

    /** Command bar: select a proposal by id. */
    void select(String proposalId) {
        pendingSelect = proposalId;
        refresh();
    }

    /** The case of the selected proposal, or null: the assistant's context on this screen. */
    String selectedCaseId() {
        return selected().map(ProposalRow::caseId).filter(id -> !id.isEmpty()).orElse(null);
    }

    private Optional<ProposalRow> selected() {
        return Optional.ofNullable(pending.getSelectionModel().getSelectedItem());
    }

    private void describe(ProposalRow r) {
        decision.getChildren().clear();
        flows.getItems().clear();
        net.setText("");
        rejectReason.clear();
        if (r == null) {
            title.setText("No proposal selected");
            reason.setText("");
            return;
        }
        PendingAction p = r.proposal();
        title.setText(p.id() + "  " + p.command().describe());
        reason.setText(p.justification());
        var f = r.latest();
        String model = f == null || f.trace() == null ? "" : "  " + f.trace().pricing().runLabel();
        String[][] rows = {{"Proposed by", r.proposedBy() + model, null},
                {"Decided by", "HUMAN  " + gui.config.userName() + " (you)", null},
                {"Finding", r.finding().isEmpty() ? "none on the case" : r.finding(), r.findingStyle()},
                {"Claims", r.checked().isEmpty() ? "not checked" : r.checked() + " match the data", r.checkedStyle()},
                {"If approved", r.ifApproved(), null},
                {"Logged as", "audit trail, actor HUMAN", null}};
        for (int i = 0; i < rows.length; i++) {
            var value = Gui.label(rows[i][1], "mono");
            if (rows[i][2] != null) {
                value.getStyleClass().add(rows[i][2]);
            }
            decision.addRow(i, Gui.label(rows[i][0], "muted"), value);
        }
        Alert a = r.target() == null ? null : alerts.get(r.target().openingAlertId());
        if (a == null) {
            return;
        }
        gui.run("Loading the flagged money", () -> gui.facade.transactionsFor(a.accountId(), tracewise.domain.DateRange.ALL), txs -> {
            if (selected().filter(s -> s == r).isEmpty()) {
                return; // another proposal was selected meanwhile
            }
            flows.getItems().setAll(CaseFacts.flows(a, txs));
            net.setText("Net  " + CaseFacts.of(a, txs).net());
        });
    }

    /** F9. */
    void approveSelected() {
        var p = selected().map(ProposalRow::proposal).orElse(null);
        if (p == null) {
            return;
        }
        if (gui.confirm("Approve " + p.id() + "?", p.command().describe())) {
            gui.run("Approving " + p.id(), () -> gui.facade.approve(p.id()), this::decided);
        }
    }

    /** F10: the reason comes from the box under the decision, so the agent sees it if the case is investigated again. */
    void rejectSelected() {
        var p = selected().map(ProposalRow::proposal).orElse(null);
        if (p == null) {
            return;
        }
        String why = rejectReason.getText().strip();
        if (why.isEmpty()) {
            rejectReason.requestFocus();
            last.setText("Write the reason first: the agent sees it if the case is investigated again.");
            last.getStyleClass().setAll("label", "mono", "wait");
            return;
        }
        gui.run("Rejecting " + p.id(), () -> gui.facade.reject(p.id(), why), this::decided);
    }

    private void openSelected() {
        selected().map(ProposalRow::caseId).filter(id -> !id.isEmpty()).ifPresent(gui.openCase);
    }

    private void decided(PendingAction p) {
        last.setText(p.id() + "  " + Display.label(p.status()) + (p.decisionReason() == null ? "" : ": " + p.decisionReason()));
        last.getStyleClass().setAll("label", "mono", "muted");
        refresh();
    }

    private static Label section(String text) {
        return Gui.label(text, "section-title");
    }
}
