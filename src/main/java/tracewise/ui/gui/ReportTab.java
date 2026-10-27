package tracewise.ui.gui;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import tracewise.cases.state.CaseStatus;
import tracewise.domain.Case;
import tracewise.domain.Transaction;
import tracewise.reporting.ExportFormat;
import tracewise.reporting.ReportStatus;
import tracewise.reporting.StrReport;

/**
 * F11: the case view's Report tab. The agent drafts the report, the analyst edits the two text fields, requests
 * finalisation (it waits on the Approvals screen), and exports the report once it is final.
 */
class ReportTab {

    private static final String BOTH = "HTML and JSON";

    private final Gui gui;
    private final Consumer<String> changed;
    private final Label status = new Label("Select a case");
    private final Label subject = new Label();
    private final TableView<Transaction> transactions = new TableView<>();
    private final TextArea grounds = new TextArea();
    private final TextArea actions = new TextArea();
    private final Button draft = new Button("Draft with agent");
    private final Button save = new Button("Save edits");
    private final Button finalise = new Button("Request finalisation");
    private final Button export = new Button("Export...");
    private Case shown;

    /** changed is called with the case id after anything that changes the case. */
    ReportTab(Gui gui, Consumer<String> changed) {
        this.gui = gui;
        this.changed = changed;
    }

    Node build() {
        status.getStyleClass().add("section-title");
        subject.getStyleClass().add("muted");
        transactions.getColumns().setAll(List.of(Gui.column("Transaction", Transaction::id, 110),
                Gui.column("Time (UTC)", Transaction::timestamp, 130), Gui.column("From", Transaction::senderAccountId, 90),
                Gui.column("To", Transaction::receiverAccountId, 90), Gui.column("Amount (CAD)", Transaction::amountCad, 110),
                Gui.column("Channel", Transaction::channel, 90), Gui.column("Country", Transaction::counterpartyCountry, 70)));
        transactions.setPlaceholder(new Label("No confirmed transactions."));
        transactions.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        transactions.setPrefHeight(150);
        for (TextArea t : List.of(grounds, actions)) {
            t.setWrapText(true);
        }
        grounds.setPrefRowCount(9);
        actions.setPrefRowCount(5);
        draft.setOnAction(e -> onDraftReport());
        save.setOnAction(e -> onSaveReport());
        finalise.getStyleClass().add("primary");
        finalise.setOnAction(e -> onRequestFinalisation());
        export.setOnAction(e -> onExport());
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var buttons = new HBox(8, draft, save, spacer, finalise, export);
        var note = new Label("Nothing is sent to FINTRAC. Finalisation needs the case escalated, no empty field, and no "
                + "rejected claim left in the text.");
        note.getStyleClass().add("muted");
        note.setWrapText(true);
        var pane = new VBox(6, status, subject, buttons, new Label("Transactions (from the confirmed claims)"),
                transactions, new Label("Grounds for suspicion"), grounds, new Label("Actions taken"), actions, note);
        VBox.setVgrow(grounds, Priority.ALWAYS);
        pane.setPadding(new Insets(8));
        show(null);
        return pane;
    }

    /** The case on screen, or null for none. */
    void show(Case c) {
        shown = c;
        StrReport r = c == null ? null : c.report().orElse(null);
        boolean isDraft = r != null && r.status() == ReportStatus.DRAFT;
        status.setText(c == null ? "Select a case" : r == null ? "No report yet"
                : r.status() == ReportStatus.FINAL ? "FINAL, locked " + Gui.text(r.finalisedAt()) + " UTC" : "DRAFT");
        subject.setText(r == null ? (c != null && c.latestFinding() == null ? "Investigate the case first." : "")
                : "Subject account " + r.subjectAccountId());
        transactions.getItems().setAll(r == null ? List.of() : r.transactions());
        grounds.setText(r == null ? "" : r.groundsForSuspicion());
        actions.setText(r == null ? "" : r.actionsTaken());
        grounds.setEditable(isDraft);
        actions.setEditable(isDraft);
        draft.setText(r == null ? "Draft with agent" : "Redraft with agent");
        draft.setDisable(c == null || c.latestFinding() == null || c.status() == CaseStatus.CLOSED
                || (r != null && !isDraft));
        save.setDisable(!isDraft);
        finalise.setDisable(!isDraft || c.status() != CaseStatus.ESCALATED);
        export.setDisable(r == null || isDraft);
    }

    private void onDraftReport() {
        String id = shown.id();
        if (shown.report().isPresent() && !gui.confirm("Redraft the report for " + id,
                "The agent writes a new draft. Your edits to the current draft are replaced.")) {
            return;
        }
        status.setText("The agent is drafting the report...");
        gui.run("Drafting the report for " + id, () -> gui.facade.draftReport(id, true), r -> changed.accept(id));
    }

    private void onSaveReport() {
        String id = shown.id();
        String g = grounds.getText();
        String a = actions.getText();
        gui.run("Saving the report for " + id, () -> gui.facade.saveReport(id, g, a), r -> changed.accept(id));
    }

    /** Unsaved edits are saved first, so what waits for approval is what is on screen. */
    private void onRequestFinalisation() {
        String id = shown.id();
        String g = grounds.getText();
        String a = actions.getText();
        StrReport saved = shown.report().orElseThrow();
        gui.run("Requesting finalisation for " + id, () -> {
            if (!saved.groundsForSuspicion().equals(g) || !saved.actionsTaken().equals(a)) {
                gui.facade.saveReport(id, g, a);
            }
            return gui.facade.requestFinalisation(id);
        }, p -> {
            changed.accept(id);
            gui.info("Waiting for approval", "Finalisation of the report for " + id + " waits as " + p.id()
                    + ". An analyst or supervisor approves it on the Approvals screen.");
        });
    }

    private void onExport() {
        String id = shown.id();
        var format = new ChoiceDialog<>(BOTH, BOTH, "HTML", "JSON");
        format.initOwner(gui.stage);
        format.setTitle("Export the report for " + id);
        format.setHeaderText("Which format?");
        var chosen = format.showAndWait();
        if (chosen.isEmpty()) {
            return;
        }
        var chooser = new DirectoryChooser();
        chooser.setTitle("Folder for the report files");
        var folder = chooser.showDialog(gui.stage);
        if (folder == null) {
            return;
        }
        var formats = BOTH.equals(chosen.get()) ? EnumSet.allOf(ExportFormat.class)
                : EnumSet.of(ExportFormat.valueOf(chosen.get()));
        Path dir = folder.toPath();
        gui.run("Exporting the report for " + id, () -> gui.facade.exportReport(id, formats, dir),
                files -> gui.info("Report exported", "Saved:\n" + String.join("\n", files.stream().map(Path::toString)
                        .toList())));
    }
}
