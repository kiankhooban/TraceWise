package tracewise.ui.gui;

import java.util.function.Supplier;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import tracewise.agent.AssistantAnswer;
import tracewise.agent.AssistantContext;

/** F10: the assistant panel beside every screen. It reads only; its cited transactions are verified. */
final class AssistantView {

    private final Gui gui;
    private final Supplier<AssistantContext> context;
    private final VBox messages = new VBox(10);
    private final TextField question = new TextField();

    /** context: what the current screen has selected (a case or an account), sent with each question (Stage 1 F10). */
    AssistantView(Gui gui, Supplier<AssistantContext> context) {
        this.gui = gui;
        this.context = context;
    }

    Node build() {
        messages.setPadding(new Insets(12));
        var scroll = new ScrollPane(messages);
        scroll.setFitToWidth(true);
        messages.heightProperty().addListener((o, a, b) -> scroll.setVvalue(1.0));
        question.setPromptText("ask about an account, alert or case");
        question.getStyleClass().add("command");
        question.setOnAction(e -> onSubmit(question.getText()));
        var ask = Gui.button("Ask", () -> onSubmit(question.getText()));
        ask.getStyleClass().add("primary");
        HBox.setHgrow(question, Priority.ALWAYS);
        var input = new HBox(6, Gui.label(">", "command-mark"), question, ask);
        input.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        input.setPadding(new Insets(6, 8, 6, 0));
        input.setStyle("-fx-border-color: -tw-line transparent transparent transparent;");
        var root = new BorderPane(scroll);
        var spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        root.setTop(Gui.panelHead("ASK", "Assistant", spacer, Gui.label("reads only", "muted")));
        root.setBottom(input);
        root.setMinWidth(300);
        return root;
    }

    void onSubmit(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        question.clear();
        var asked = context.get();
        messages.getChildren().addAll(Gui.label("> " + text, "chat-q"), Gui.label(SearchResults.contextLabel(asked), "chat-meta"));
        gui.worker.submit("Asking the assistant", () -> gui.facade.ask(text, asked),
                this::answered, e -> add(e.getMessage() == null ? e.toString() : e.getMessage(), "chat-error", List.of()));
    }

    private void answered(AssistantAnswer a) {
        var meta = new java.util.ArrayList<Label>();
        if (a.kind() != AssistantAnswer.Kind.ANSWER) {
            meta.add(Gui.label(a.kind().name(), "chat-meta", "wait"));
        }
        var report = a.verification();
        if (a.kind() == AssistantAnswer.Kind.ANSWER && report != null) {
            meta.add(Gui.label("Checked  " + Display.checkCode(report) + " " + report.summary(), "chat-meta", Display.checkStyle(report)));
            for (var check : report.checks()) { // a badge per cited claim (Stage 1 F10)
                var line = Gui.label((check.confirmed() ? "VER  " : "REJ  ") + check.claim()
                        + (check.confirmed() ? "" : "  (" + check.reason() + ")"), "chat-meta", check.confirmed() ? "ok" : "bad");
                line.setWrapText(true);
                meta.add(line);
            }
        }
        var block = add(a.text(), "chat-a", meta);
        if (a.trace() != null) {
            SearchResults.from(a.trace().steps()).ifPresent(r -> block.getChildren().add(table(r)));
        }
    }

    /** Search results as a table (Stage 1 F10); double-click a row with a case to open it. */
    private Node table(SearchResults r) {
        var table = new javafx.scene.control.TableView<List<String>>();
        for (int c = 0; c < r.columns().size(); c++) {
            int col = c;
            table.getColumns().add(Gui.column(r.columns().get(c), (List<String> row) -> row.get(col), 96, "mono"));
        }
        table.getItems().setAll(r.rows());
        table.setPlaceholder(new Label("Nothing matched."));
        table.setPrefHeight(Math.min(26 * 8, 30 + 26 * Math.max(1, r.rows().size())));
        int caseColumn = r.columns().indexOf("case id");
        table.setRowFactory(t -> {
            var row = new javafx.scene.control.TableRow<List<String>>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty() && caseColumn >= 0 && !row.getItem().get(caseColumn).isEmpty()) {
                    gui.openCase.accept(row.getItem().get(caseColumn));
                }
            });
            return row;
        });
        return new VBox(4, Gui.label("Results  " + r.title(), "chat-meta"), table);
    }

    private VBox add(String text, String style, List<Label> meta) {
        var body = new Label(text);
        body.setWrapText(true);
        body.getStyleClass().add(style);
        var block = new VBox(6, body);
        block.getChildren().addAll(meta);
        block.getStyleClass().add("chat");
        messages.getChildren().add(block);
        return block;
    }
}
