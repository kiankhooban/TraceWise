package tracewise.persistence;

import java.sql.SQLException;

public class DatabaseException extends RuntimeException {
    public DatabaseException(SQLException cause) {
        super(cause.getMessage(), cause);
    }
}
