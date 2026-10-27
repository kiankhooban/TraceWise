package tracewise.ui.gui;

import javafx.application.Application;

/** Starts the GUI. A separate class, because JavaFX loaded from the class path needs a main class that is not the Application. */
public final class GuiMain {

    private GuiMain() {
    }

    public static void main(String[] args) {
        Application.launch(TraceWiseApp.class, args);
    }
}
