package util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Legacy DBConnection class - now wraps DatabaseFactory.
 * Kept for backwards compatibility with existing code.
 * 
 * @deprecated Use DatabaseFactory instead for new code.
 */
public class DBConnection {

    private static final String URL =
            "jdbc:mysql://localhost:3306/hospital?useSSL=true&serverTimezone=UTC";

    private static String username;
    private static String password;

    public static void setCredentials(String user, String pass) {
        username = user;
        password = pass;
        // Also set in new DatabaseConfig for compatibility
        DatabaseConfig.setMySQLCredentials(user, pass);
    }

    public static Connection getConnection() throws SQLException {
        // Use new DatabaseFactory if credentials are set there
        try {
            return DatabaseFactory.getConnection();
        } catch (SQLException e) {
            // Fallback to legacy behavior
            if (username == null || password == null) {
                throw new SQLException("Database credentials not set.");
            }
            return DriverManager.getConnection(URL, username, password);
        }
    }
}
