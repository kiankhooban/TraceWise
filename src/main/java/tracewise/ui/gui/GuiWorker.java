package tracewise.ui.gui;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Runs every facade call on one background thread, one at a time and in order, so the screen never freezes
 * and the single database connection is never used by two threads. Results, errors and status changes are
 * handed back through the ui executor (Platform::runLater in the app). Cancel and stop requests bypass it:
 * they only set flags.
 */
final class GuiWorker implements AutoCloseable {

    private final Executor ui;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        var t = new Thread(r, "tracewise-worker");
        t.setDaemon(true);
        return t;
    });
    private volatile Consumer<String> status = s -> { };

    GuiWorker(Executor ui) {
        this.ui = ui;
    }

    /** Reports the label of the task running, or "" when idle; called on the UI thread. */
    void onStatus(Consumer<String> listener) {
        status = listener;
    }

    <T> void submit(String label, Callable<T> work, Consumer<T> onDone, Consumer<Exception> onError) {
        worker.execute(() -> {
            ui.execute(() -> status.accept(label));
            try {
                T result = work.call();
                ui.execute(() -> onDone.accept(result));
            } catch (Exception e) {
                ui.execute(() -> onError.accept(e));
            } finally {
                ui.execute(() -> status.accept(""));
            }
        });
    }

    /** Finishes the queued tasks (waiting up to a minute), then stops the thread. */
    @Override
    public void close() throws InterruptedException {
        worker.shutdown();
        worker.awaitTermination(1, TimeUnit.MINUTES);
    }
}
