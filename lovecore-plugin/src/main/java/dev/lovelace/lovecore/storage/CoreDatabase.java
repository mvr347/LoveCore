package dev.lovelace.lovecore.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * The one SQLite file of the core ({@code lovecore.db}) for settings that belong to players, such as
 * the LoveNotify channels. These are preferences, not progress: {@code /lovecoreadmin cleardata} does
 * not touch them. One connection, used by one writer thread at a time.
 */
public final class CoreDatabase implements AutoCloseable {

    private final Connection connection;

    private CoreDatabase(Connection connection) {
        this.connection = connection;
    }

    public static CoreDatabase open(File file) throws SQLException {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Cannot create " + parent);
        }
        // The driver is called directly: the shaded copy is not found through DriverManager's service lookup.
        Connection connection = new org.sqlite.JDBC().connect("jdbc:sqlite:" + file.getAbsolutePath(), new Properties());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("CREATE TABLE IF NOT EXISTS notify_settings ("
                    + "uuid TEXT NOT NULL, channel TEXT NOT NULL, enabled INTEGER NOT NULL, "
                    + "PRIMARY KEY (uuid, channel))");
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return new CoreDatabase(connection);
    }

    public Connection connection() {
        return connection;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Nothing left to save at this point.
        }
    }
}
