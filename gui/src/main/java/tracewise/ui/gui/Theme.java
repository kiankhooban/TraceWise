package tracewise.ui.gui;

import java.io.IOException;
import java.io.InputStream;
import java.util.prefs.Preferences;
import javafx.scene.Scene;
import javafx.scene.text.Font;

/** The bundled fonts and the dark or day stylesheet (docs/stage2/ui-design.md). */
final class Theme {

    private static final String[] FONTS = {"SourceSans3-Regular.ttf", "SourceSans3-Medium.ttf",
            "SourceSans3-Semibold.ttf", "JetBrainsMono-Regular.ttf", "JetBrainsMono-Medium.ttf",
            "JetBrainsMono-SemiBold.ttf"};
    private static final String KEY = "day";

    private Theme() {
    }

    /** JavaFX names each static weight as its own family, e.g. "Source Sans 3 Semibold"; gui.css uses those names. */
    static void loadFonts() {
        for (String file : FONTS) {
            try (InputStream in = Theme.class.getResourceAsStream("/tracewise/fonts/" + file)) {
                if (in != null) {
                    Font.loadFont(in, 13);
                }
            } catch (IOException e) {
                // a missing font falls back to the system font; the app still works
            }
        }
    }

    /** TRACEWISE_THEME=day or dark wins; otherwise the analyst's last choice; dark by default. */
    static boolean day() {
        String env = System.getenv("TRACEWISE_THEME");
        if (env != null && !env.isBlank()) {
            return env.strip().equalsIgnoreCase("day");
        }
        return Preferences.userNodeForPackage(Theme.class).getBoolean(KEY, false);
    }

    static void apply(Scene scene, boolean day) {
        scene.getStylesheets().setAll(Theme.class.getResource("/tracewise/gui.css").toExternalForm());
        if (day) {
            scene.getStylesheets().add(Theme.class.getResource("/tracewise/day.css").toExternalForm());
        }
    }

    static void remember(boolean day) {
        Preferences.userNodeForPackage(Theme.class).putBoolean(KEY, day);
    }
}
