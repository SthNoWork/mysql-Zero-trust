package util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Factory class for getting database connections.
 * Supports MySQL and Supabase PostgreSQL based on DatabaseConfig setting.
 */
public class DatabaseFactory {

    // MySQL Configuration
    private static final String MYSQL_URL = 
        "jdbc:mysql://localhost:3306/hospital?useSSL=true&serverTimezone=UTC";

    // Supabase PostgreSQL Configuration
    private static final String SUPABASE_HOST = "xvlilgsawbqpedmrbdkv.supabase.co";
    private static final int SUPABASE_PORT = 5432;
    private static final String SUPABASE_DB = "postgres";
    private static final String SUPABASE_URL = 
        "jdbc:postgresql://" + SUPABASE_HOST + ":" + SUPABASE_PORT + "/" + SUPABASE_DB + "?sslmode=require";

    /**
     * Get a database connection based on current configuration.
     */
    public static Connection getConnection() throws SQLException {
        DatabaseConfig.DatabaseType dbType = DatabaseConfig.getDatabaseType();

        switch (dbType) {
            case MYSQL:
                String mysqlUser = DatabaseConfig.getMySQLUsername();
                String mysqlPass = DatabaseConfig.getMySQLPassword();
                if (mysqlUser == null || mysqlPass == null) {
                    throw new SQLException("MySQL credentials not set. Call DatabaseConfig.setMySQLCredentials() first.");
                }
                return DriverManager.getConnection(MYSQL_URL, mysqlUser, mysqlPass);

            case SUPABASE_POSTGRESQL:
                String supabaseUser = DatabaseConfig.getSupabaseUsername();
                String supabasePass = DatabaseConfig.getSupabasePassword();
                return DriverManager.getConnection(SUPABASE_URL, supabaseUser, supabasePass);

            default:
                throw new SQLException("Unknown database type: " + dbType);
        }
    }

    /**
     * Get the table name based on database type.
     * MySQL uses hospital.Hospital_Records, Supabase uses public.hospital_records
     */
    public static String getTableName() {
        DatabaseConfig.DatabaseType dbType = DatabaseConfig.getDatabaseType();
        switch (dbType) {
            case MYSQL:
                return "Hospital_Records";
            case SUPABASE_POSTGRESQL:
                return "public.hospital_records";
            default:
                return "Hospital_Records";
        }
    }

    /**
     * Check if current database is PostgreSQL (for SQL syntax differences)
     */
    public static boolean isPostgreSQL() {
        return DatabaseConfig.getDatabaseType() == DatabaseConfig.DatabaseType.SUPABASE_POSTGRESQL;
    }
}
