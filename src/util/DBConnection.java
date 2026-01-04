package util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    private static final String URL =
            "jdbc:mysql://192.168.0.117:3306/hospital?useSSL=true&serverTimezone=UTC";

    private static String username = "webapp_user";
    private static String password = "STRONG_RANDOM_PASSOWRD";
    private static Connection instance;

    public static void setCredentials(String user, String pass) {
        username = user;
        password = pass;
        // Force reconnection on next call
        close();
    }

    public static synchronized Connection getConnection() throws SQLException {
        if (username == null || password == null) {
            throw new SQLException("Database credentials not set.");
        }
        
        if (instance == null || instance.isClosed() || !instance.isValid(2)) {
            // Close silently if needed
            close();
            instance = DriverManager.getConnection(URL, username, password);
        }
        return instance;
    }

    public static synchronized void close() {
        if (instance != null) {
            try {
                instance.close();
            } catch (SQLException e) {
                // Ignore
            } finally {
                instance = null;
            }
        }
    }
}
