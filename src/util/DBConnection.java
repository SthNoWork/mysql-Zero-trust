package util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    private static final String URL =
            "jdbc:mysql://localhost:3306/hospital?useSSL=false&serverTimezone=UTC";

    private static String username;
    private static String password;

    public static void setCredentials(String user, String pass) {
        username = user;
        password = pass;
    }

    public static Connection getConnection() throws SQLException {
        if (username == null || password == null) {
            throw new SQLException("Database credentials not set.");
        }
        return DriverManager.getConnection(URL, username, password);
    }
}
