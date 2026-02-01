package util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SupabaseDBConnection {

    private static final String HOST = "xvlilgsawbqpedmrbdkv.supabase.co";
    private static final int PORT = 5432;
    private static final String DB = "postgres";
    private static final String JDBC_URL = "jdbc:postgresql://" + HOST + ":" + PORT + "/" + DB + "?sslmode=require";

    private static String username = System.getenv("SUPABASE_DB_USER");
    private static String password = System.getenv("SUPABASE_DB_PASS");

    // NOTE: per request, provide a fallback to the project's anon key if env vars
    // are not set. This is insecure for production but convenient for quick testing.
    static {
        if (username == null) username = "postgres";
        if (password == null) password = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inh2bGlsZ3Nhd2JxcGVkbXJiZGt2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjgxMjExMDYsImV4cCI6MjA4MzY5NzEwNn0.6_k63XlmfDIJ2jN0txMjcY-SKwYH7H_HF4b-3NrDKbA";
    }
    // Callers may still override via `setCredentials` if needed.

    public static void setCredentials(String user, String pass) {
        username = user;
        password = pass;
    }

    // Editable schema and table names (not required for obtaining a Connection,
    // but convenient for repository code that needs a qualified table reference)
    private static String schemaName;
    private static String tableName;

    public static void setSchemaName(String schema) {
        schemaName = schema;
    }

    public static void setTableName(String table) {
        tableName = table;
    }

    public static String getSchemaName() {
        return schemaName;
    }

    public static String getTableName() {
        return tableName;
    }

    public static String getQualifiedTable() {
        if (schemaName == null || tableName == null) return null;
        return schemaName + "." + tableName;
    }

    public static Connection getConnection() throws SQLException {
        if (username == null || password == null) {
            throw new SQLException("Supabase DB credentials not set.");
        }
        return DriverManager.getConnection(JDBC_URL, username, password);
    }
}
