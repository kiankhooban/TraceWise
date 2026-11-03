package tracewise.ui.gui;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import tracewise.app.AppConfig;
import tracewise.app.TraceWiseFacade;

/** What every view shares: the facade, the worker that calls it, the window, and small display helpers. */
final class Gui {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    final TraceWiseFacade facade;
    final GuiWorker worker;
    final Stage stage;
    final AppConfig config;
    /** Opens a case in the Cases screen; set by TraceWiseApp once the views exist. */
    Consumer<String> openCase = id -> { };
    /** Starts an investigation of an alert in the Cases screen; set by TraceWiseApp. */
    Consumer<String> investigate = id -> { };

    Gui(TraceWiseFacade facade, GuiWorker worker, Stage stage, AppConfig config) {
        this.facade = facade;
        this.worker = worker;
        this.stage = stage;
        this.config = config;
    }

    /** Runs facade work off the UI thread; errors are shown in a dialog. */
    <T> void run(String label, Callable<T> work, Consumer<T> onDone) {
        worker.submit(label, work, onDone, this::error);
    }

    void error(Exception e) {
        String message = e.getMessage() == null ? e.toString() : e.getMessage();
        show(javafx.scene.control.Alert.AlertType.ERROR, "Something went wrong", message);
    }

    void info(String title, String text) {
        show(javafx.scene.control.Alert.AlertType.INFORMATION, title, text);
    }

    boolean confirm(String title, String text) {
        var d = dialog(javafx.scene.control.Alert.AlertType.CONFIRMATION, title, text);
        return d.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    private void show(javafx.scene.control.Alert.AlertType type, String title, String text) {
        dialog(type, title, text).showAndWait();
    }

    private javafx.scene.control.Alert dialog(javafx.scene.control.Alert.AlertType type, String title, String text) {
        var d = new javafx.scene.control.Alert(type);
        d.initOwner(stage);
        d.setTitle(title);
        d.setHeaderText(title);
        var body = new TextArea(text);
        body.setEditable(false);
        body.setWrapText(true);
        body.setPrefRowCount(Math.min(16, Math.max(3, text.split("\n").length + 1)));
        d.getDialogPane().setContent(body);
        return d;
    }

    static <S> TableColumn<S, String> column(String title, Function<S, Object> value, double width) {
        var c = new TableColumn<S, String>(title);
        c.setCellValueFactory(cell -> new SimpleStringProperty(text(value.apply(cell.getValue()))));
        c.setPrefWidth(width);
        return c;
    }

    /** A column with style classes on its header and cells ("mono", "num"). */
    static <S> TableColumn<S, String> column(String title, Function<S, Object> value, double width, String... classes) {
        return styled(title, value, s -> null, width, classes);
    }

    /** As column(...), plus a per-row state class (e.g. "risk-high") so colour always comes with the cell's words. */
    static <S> TableColumn<S, String> styled(String title, Function<S, Object> value, Function<S, String> state,
            double width, String... classes) {
        var c = column(title, value, width);
        c.getStyleClass().addAll(classes);
        c.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            private String applied;

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (applied != null) {
                    getStyleClass().remove(applied);
                    applied = null;
                }
                S row = empty || getTableRow() == null ? null : getTableRow().getItem();
                if (row != null) {
                    applied = state.apply(row);
                    if (applied != null) {
                        getStyleClass().add(applied);
                    }
                }
            }
        });
        return c;
    }

    /** A button that never truncates its label. */
    static javafx.scene.control.Button button(String text, Runnable action) {
        var b = new javafx.scene.control.Button(text);
        b.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        b.setOnAction(e -> action.run());
        return b;
    }

    /** A panel title strip: a short code, a name, and optional details after it. */
    static javafx.scene.layout.HBox panelHead(String code, String name, javafx.scene.Node... rest) {
        var codeLabel = new javafx.scene.control.Label(code);
        codeLabel.getStyleClass().add("panel-code");
        var nameLabel = new javafx.scene.control.Label(name);
        nameLabel.getStyleClass().add("panel-name");
        nameLabel.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE); // CSS cannot say "min = pref"
        var head = new javafx.scene.layout.HBox(codeLabel, nameLabel);
        head.getChildren().addAll(rest);
        head.getStyleClass().add("panel-head");
        return head;
    }

    static javafx.scene.control.Label label(String text, String... classes) {
        var l = new javafx.scene.control.Label(text);
        l.getStyleClass().addAll(classes);
        return l;
    }

    static String text(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal d) {
            return cad(d);
        }
        if (value instanceof Instant t) {
            return TIME.format(t);
        }
        return value.toString();
    }

    static String cad(BigDecimal amount) {
        return String.format("%,.2f", amount);
    }
}
