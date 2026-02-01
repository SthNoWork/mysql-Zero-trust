package util;

/**
 * Database configuration manager.
 * Switch between MySQL and Supabase PostgreSQL by changing the DB_TYPE.
 */
public class DatabaseConfig {

    public enum DatabaseType {
        MYSQL,
        SUPABASE_POSTGRESQL
    }

    // ========== CHANGE THIS TO SWITCH DATABASE ==========
    private static DatabaseType DB_TYPE = DatabaseType.MYSQL;
    // ====================================================

    private static String mysqlUsername;
    private static String mysqlPassword;

    public static DatabaseType getDatabaseType() {
        return DB_TYPE;
    }

    public static void setDatabaseType(DatabaseType type) {
        DB_TYPE = type;
    }

    // MySQL credentials
    public static void setMySQLCredentials(String user, String pass) {
        mysqlUsername = user;
        mysqlPassword = pass;
    }

    public static String getMySQLUsername() {
        return mysqlUsername;
    }

    public static String getMySQLPassword() {
        return mysqlPassword;
    }

    // Supabase credentials (uses environment variables or defaults)
    public static String getSupabaseUsername() {
        String user = System.getenv("SUPABASE_DB_USER");
        return user != null ? user : "postgres";
    }

    public static String getSupabasePassword() {
        String pass = System.getenv("SUPABASE_DB_PASS");
        // Fallback to anon key for testing (insecure for production)
        return pass != null ? pass : "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inh2bGlsZ3Nhd2JxcGVkbXJiZGt2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjgxMjExMDYsImV4cCI6MjA4MzY5NzEwNn0.6_k63XlmfDIJ2jN0txMjcY-SKwYH7H_HF4b-3NrDKbA";
    }
}
