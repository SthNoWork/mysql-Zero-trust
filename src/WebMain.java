import server.E2EEWebServer;
import util.DatabaseConfig;

/**
 * E2EE Web Server Entry Point
 * 
 * This server implements End-to-End Encryption where:
 * - ALL medical data encryption/decryption happens on CLIENT devices
 * - Server NEVER decrypts medical data
 * - Server NEVER stores or accesses client private keys
 * - RBAC is enforced using metadata, not decrypted content
 * 
 * To switch database:
 *   Change DatabaseConfig.DB_TYPE in DatabaseConfig.java
 *   or pass command line argument: java WebMain mysql|supabase
 */
public class WebMain {
    public static void main(String[] args) {
        try {
            // Parse command line argument for database type
            if (args.length > 0) {
                String dbArg = args[0].toLowerCase();
                if ("supabase".equals(dbArg) || "postgresql".equals(dbArg) || "postgres".equals(dbArg)) {
                    DatabaseConfig.setDatabaseType(DatabaseConfig.DatabaseType.SUPABASE_POSTGRESQL);
                    System.out.println("📦 Using Supabase PostgreSQL");
                } else if ("mysql".equals(dbArg)) {
                    DatabaseConfig.setDatabaseType(DatabaseConfig.DatabaseType.MYSQL);
                    System.out.println("📦 Using MySQL");
                }
            }
            
            System.out.println("\n🔐 Starting E2EE Hospital Server...\n");
            
            E2EEWebServer server = new E2EEWebServer();
            server.start();
            
        } catch (Exception e) {
            System.err.println("❌ Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
