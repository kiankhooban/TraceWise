package tracewise.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * The single SQLite connection (WAL mode). All writes go through it, so the UI thread and
 * background agent work never contend for a database lock.
 */
public class Database implements AutoCloseable {

    private final Connection connection;

    private Database(Connection connection) {
        this.connection = connection;
    }

    public static Database open(Path file) {
        try {
            var db = new Database(DriverManager.getConnection("jdbc:sqlite:" + file));
            try (Statement s = db.connection.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA foreign_keys=ON");
                // statements end with ";" at a line end followed by the next CREATE, so trigger bodies stay whole
                for (String ddl : schema().split(";\\s*\\n(?=CREATE|\\z)")) {
                    if (!ddl.isBlank()) {
                        s.execute(ddl);
                    }
                }
            }
            return db;
        } catch (SQLException e) {
            throw new DatabaseException(e);
        }
    }

    public Connection connection() {
        return connection;
    }

    /** Like inTransaction(Runnable), returning what the work produced. */
    public <T> T inTransaction(java.util.function.Supplier<T> work) {
        var result = new java.util.ArrayList<T>(1);
        inTransaction(() -> {
            result.add(work.get());
        });
        return result.get(0);
    }

    /** Runs work atomically; nested calls join the outer transaction. */
    public void inTransaction(Runnable work) {
        try {
            if (!connection.getAutoCommit()) {
                work.run();
                return;
            }
            connection.setAutoCommit(false);
            try {
                work.run();
                connection.commit();
            } catch (RuntimeException | Error e) {
                // an Error (out of memory) must roll back too: the finally block would otherwise commit
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseException(e);
        }
    }

    String journalMode() {
        try (Statement s = connection.createStatement(); var rs = s.executeQuery("PRAGMA journal_mode")) {
            return rs.getString(1);
        } catch (SQLException e) {
            throw new DatabaseException(e);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new DatabaseException(e);
        }
    }

    private static String schema() {
        try (InputStream in = Database.class.getResourceAsStream("/tracewise/schema.sql")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
