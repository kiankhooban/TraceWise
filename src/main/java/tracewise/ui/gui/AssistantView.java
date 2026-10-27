package tracewise.ui.gui;

import java.util.function.Supplier;
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
        var title = new Label("Assistant");
        title.getStyleClass().add("section-title");
        var hint = new Label("Ask about accounts, alerts and cases. It reads only; changes go through investigations "
                + "and approvals.");
        hint.setWrapText(true);
        hint.getStyleClass().add("muted");
        messages.setPadding(new Insets(4));
        var scroll = new ScrollPane(messages);
        scroll.setFitToWidth(true);
        messages.heightProperty().addListener((o, a, b) -> scroll.setVvalue(1.0));
        question.setPromptText("Why is account C1037 risky?");
        question.setOnAction(e -> onSubmit(question.getText()));
        var ask = new Button("Ask");
        ask.getStyleClass().add("primary");
        ask.setOnAction(e -> onSubmit(question.getText()));
        HBox.setHgrow(question, Priority.ALWAYS);
        var input = new HBox(6, question, ask);
        var root = new BorderPane(scroll);
        root.setTop(new VBox(4, title, hint));
        root.setBottom(input);
        BorderPane.setMargin(scroll, new Insets(8, 0, 8, 0));
        root.setPadding(new Insets(10));
        root.setMinWidth(300);
        return root;
    }

    void onSubmit(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        question.clear();
        add("You", text, "chat-user");
        String caseId = currentCase.get();
        gui.worker.submit("Asking the assistant", () -> gui.facade.ask(text, new AssistantContext(caseId, null)),
                this::answered, e -> add("Assistant", e.getMessage(), "chat-error"));
    }

    private void answered(AssistantAnswer a) {
        String cited = a.claims().isEmpty() ? "" : "\nCited: " + String.join(", ",
                a.claims().stream().map(c -> c.transactionId()).toList());
        String check = a.kind() == AssistantAnswer.Kind.ANSWER
                ? "\nVerification: " + a.verification().status() + ", " + a.verification().summary() : "";
        add("Assistant" + (a.kind() == AssistantAnswer.Kind.ANSWER ? "" : " (" + a.kind().name().toLowerCase() + ")"),
                a.text() + cited + check, "chat-assistant");
    }

    private void add(String who, String text, String style) {
        var name = new Label(who);
        name.getStyleClass().add("chat-name");
        var body = new Label(text);
        body.setWrapText(true);
        var bubble = new VBox(2, name, body);
        bubble.getStyleClass().addAll("chat", style);
        messages.getChildren().add(bubble);
    }
}
