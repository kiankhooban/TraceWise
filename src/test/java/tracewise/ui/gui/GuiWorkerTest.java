package tracewise.ui.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** All facade work runs on one background thread, in order; results come back on the UI thread. */
class GuiWorkerTest {

    final List<String> log = Collections.synchronizedList(new ArrayList<>());
    final List<String> uiThreads = Collections.synchronizedList(new ArrayList<>());

    /** Stands in for Platform.runLater: runs the callback at once and remembers the thread it came from. */
    final java.util.concurrent.Executor ui = r -> {
        uiThreads.add(Thread.currentThread().getName());
        r.run();
    };

    @Test
    void runsTasksInOrderOnOneBackgroundThread() throws Exception {
        var threads = Collections.synchronizedList(new ArrayList<String>());
        try (var worker = new GuiWorker(ui)) {
            for (int i = 0; i < 3; i++) {
                int n = i;
                worker.submit("task " + n, () -> {
                    threads.add(Thread.currentThread().getName());
                    Thread.sleep(20 - 5 * n);
                    return n;
                }, r -> log.add("done " + r), e -> log.add("error"));
            }
        }

        assertEquals(List.of("done 0", "done 1", "done 2"), log);
        assertEquals(1, threads.stream().distinct().count());
        assertNotEquals(Thread.currentThread().getName(), threads.get(0));
    }

    @Test
    void aFailureGoesToTheErrorCallbackAndLaterTasksStillRun() throws Exception {
        try (var worker = new GuiWorker(ui)) {
            worker.submit("bad", () -> {
                throw new IllegalStateException("No alert ALR-9");
            }, r -> log.add("done"), e -> log.add("error: " + e.getMessage()));
            worker.submit("good", () -> 1, r -> log.add("done " + r), e -> log.add("error"));
        }

        assertEquals(List.of("error: No alert ALR-9", "done 1"), log);
    }

    @Test
    void reportsWhatItIsBusyWith() throws Exception {
        var statuses = Collections.synchronizedList(new ArrayList<String>());
        try (var worker = new GuiWorker(ui)) {
            worker.onStatus(statuses::add);
            worker.submit("Investigating ALR-00001", () -> 1, r -> { }, e -> { });
        }

        assertEquals(List.of("Investigating ALR-00001", ""), statuses);
        assertTrue(uiThreads.size() >= 3, "status and result callbacks go through the UI executor");
    }

    @Test
    void anErrorReachesTheErrorCallbackToo() throws Exception {
        try (var worker = new GuiWorker(ui)) {
            worker.submit("broken", () -> {
                throw new ExceptionInInitializerError("native library failed to load");
            }, r -> log.add("done"), e -> log.add("error: " + e.getMessage()));
        }

        assertEquals(1, log.size());
        assertTrue(log.get(0).contains("native library failed to load"), log.toString());
    }
}
