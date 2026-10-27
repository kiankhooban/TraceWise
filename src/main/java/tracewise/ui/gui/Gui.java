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
