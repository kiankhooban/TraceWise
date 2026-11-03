package tracewise.ui.gui;

import java.util.function.Supplier;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
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
    private final Supplier<String> currentCase;
    private final VBox messages = new VBox(10);
    private final TextField question = new TextField();

    /** currentCase: the case open in the Cases screen, or null; sent as context with each question. */
    AssistantView(Gui gui, Supplier<String> currentCase) {
        this.gui = gui;
        this.currentCase = currentCase;
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
        messages.getChildren().add(Gui.label("> " + text, "chat-q"));
        String caseId = currentCase.get();
        gui.worker.submit("Asking the assistant", () -> gui.facade.ask(text, new AssistantContext(caseId, null)),
                this::answered, e -> add(e.getMessage(), "chat-error", List.of()));
    }

    private void answered(AssistantAnswer a) {
        var meta = new java.util.ArrayList<Label>();
        if (a.kind() != AssistantAnswer.Kind.ANSWER) {
            meta.add(Gui.label(a.kind().name(), "chat-meta", "wait"));
        }
        if (!a.claims().isEmpty()) {
            meta.add(Gui.label("Cited    " + String.join(" ", a.claims().stream().map(c -> c.transactionId()).toList()),
                    "chat-meta"));
        }
        if (a.kind() == AssistantAnswer.Kind.ANSWER) {
            boolean ok = a.verification().status() == tracewise.verification.VerificationStatus.VERIFIED;
            meta.add(Gui.label("Checked  " + (ok ? "VER " : "UNVER ") + a.verification().summary(), "chat-meta",
                    ok ? "ok" : "bad"));
        }
        add(a.text(), "chat-a", meta);
    }

    private void add(String text, String style, List<Label> meta) {
        var body = new Label(text);
        body.setWrapText(true);
        body.getStyleClass().add(style);
        var block = new VBox(6, body);
        block.getChildren().addAll(meta);
        block.getStyleClass().add("chat");
        messages.getChildren().add(block);
    }
}
