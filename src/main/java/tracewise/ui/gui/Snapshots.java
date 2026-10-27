package tracewise.ui.gui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javafx.animation.PauseTransition;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;

/**
 * Screenshot mode (TRACEWISE_SNAPSHOT=folder): saves a PNG of every main screen, for checking the GUI and for the
 * report, without needing screen-recording permission.
 */
final class Snapshots {

    private Snapshots() {
    }

    /** Shows each tab in turn, saves it as NN-name.png, then runs done. Call on the UI thread. */
    static void ofTabs(Scene scene, TabPane tabs, Path dir, Runnable done) {
        next(scene, tabs, tabs.getTabs(), 0, dir, done);
    }

    private static void next(Scene scene, TabPane tabs, List<Tab> all, int i, Path dir, Runnable done) {
        if (i == all.size()) {
            done.run();
            return;
        }
        tabs.getSelectionModel().select(i);
        var pause = new PauseTransition(Duration.millis(600)); // lets tables and layouts settle
        pause.setOnFinished(e -> {
            save(scene, dir.resolve(String.format("%02d-%s.png", i + 1, all.get(i).getText().toLowerCase().replace(' ', '-'))));
            next(scene, tabs, all, i + 1, dir, done);
        });
        pause.play();
    }

    static void save(Scene scene, Path file) {
        WritableImage image = scene.snapshot(null);
        var out = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var pixels = image.getPixelReader();
        for (int y = 0; y < out.getHeight(); y++) {
            for (int x = 0; x < out.getWidth(); x++) {
                out.setRGB(x, y, pixels.getArgb(x, y));
            }
        }
        try {
            Files.createDirectories(file.getParent());
            javax.imageio.ImageIO.write(out, "png", file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
