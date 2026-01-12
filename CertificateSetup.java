import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class CertificateSetup {

    // Adjusted paths:
    // Server: src/certs/server.p12 (relative to project root)
    // Client: clients/ (relative to project root)
    private static final File SERVER_DIR = new File("src/certs").getAbsoluteFile();
    private static final File CLIENT_DIR = new File("clients").getAbsoluteFile();
    private static final File SERVER_KEYSTORE = new File(SERVER_DIR, "server.p12");

    public static void main(String[] args) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        
        try {
            // Ensure directories exist
            if (!SERVER_DIR.exists()) SERVER_DIR.mkdirs();
            if (!CLIENT_DIR.exists()) CLIENT_DIR.mkdirs();

            while (true) {
                System.out.println("\n--- Certificate Manager ---");
                System.out.println("Locations:");
                System.out.println(" - Server Key: " + SERVER_KEYSTORE.getPath());
                System.out.println(" - Client Keys: " + CLIENT_DIR.getPath());
                System.out.println("---------------------------");
                System.out.println("1. Generate Server Keystore (Reset/Init)");
                System.out.println("2. Create New Client Certificate");
                System.out.println("3. List Client Certificates in Server Truststore");
                System.out.println("4. Exit");
                System.out.print("Choose option: ");
                String choice = reader.readLine();

                if ("1".equals(choice)) {
                    generateServerCert(reader);
                } else if ("2".equals(choice)) {
                    generateClientCert(reader);
                } else if ("3".equals(choice)) {
                    listClientCerts(reader);
                } else if ("4".equals(choice)) {
                    System.out.println("Exiting...");
                    break;
                } else {
                    System.out.println("Invalid option.");
                }

                cleanupBin();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void generateServerCert(BufferedReader reader) throws Exception {
        System.out.println("\n--- Generating Server Certificate ---");
        
        // Warn about reset
        if (SERVER_KEYSTORE.exists()) {
            System.out.println("⚠️ WARNING: This will DELETE the existing server keystore and ALL client certificates!");
            System.out.println("   All clients will need new certificates after this.");
            String confirm = prompt(reader, "Are you sure you want to reset? (yes/no)", "no");
            if (!"yes".equalsIgnoreCase(confirm)) {
                System.out.println("Cancelled.");
                return;
            }
        }
        
        String pass = prompt(reader, "Enter Server Keystore Password", null);
        String cn = prompt(reader, "Enter Common Name (CN) [e.g. localhost]", "localhost");
        String ou = prompt(reader, "Enter Organizational Unit (OU)", "Hospital");
        String o = prompt(reader, "Enter Organization (O)", "Phnom Penh");
        String l = prompt(reader, "Enter City (L)", "Phnom Penh");
        String st = prompt(reader, "Enter State (ST)", "Phnom Penh");
        String c = prompt(reader, "Enter Country Code (C)", "KH");

        // Clean old server keystore
        if (SERVER_KEYSTORE.exists()) {
            if (!SERVER_KEYSTORE.delete()) {
                System.out.println("❌ Error: Could not delete existing server keystore. Is the server running?");
                System.out.println("Please stop the server and try again.");
                return;
            }
        }
        
        // Clean old client .p12 files
        if (CLIENT_DIR.exists()) {
            File[] clientFiles = CLIENT_DIR.listFiles();
            if (clientFiles != null) {
                for (File f : clientFiles) {
                    if (f.getName().endsWith(".p12")) {
                        f.delete();
                    }
                }
            }
            System.out.println("🗑️ Cleared old client .p12 files from " + CLIENT_DIR.getPath());
        }

        String dname = String.format("CN=%s, OU=%s, O=%s, L=%s, ST=%s, C=%s", cn, ou, o, l, st, c);
        
        // Use getAbsolutePath() for keytool
        runKeytool("-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048", 
                   "-storetype", "PKCS12", "-keystore", SERVER_KEYSTORE.getAbsolutePath(), 
                   "-validity", "3650", "-storepass", pass, "-dname", dname);
        
        System.out.println("✅ Server Keystore created at " + SERVER_KEYSTORE.getPath());
        System.out.println("⚠️ IMPORTANT: If you changed the password from 'password', update SimpleWebServer.java!");
    }

    private static void generateClientCert(BufferedReader reader) throws Exception {
        if (!SERVER_KEYSTORE.exists()) {
            System.out.println("❌ Error: Server keystore not found at: " + SERVER_KEYSTORE.getPath());
            System.out.println("Please run Option 1 first.");
            return;
        }

        System.out.println("\n--- Generating Client Certificate ---");
        String filename = prompt(reader, "Enter Client Filename (e.g. doctor)", null);
        // if (filename == null || filename.trim().isEmpty()) return; // prompt handles null now

        String clientPass = prompt(reader, "Enter Password for Client .p12", null);
        String serverPass = prompt(reader, "Enter Server Keystore Password (to import cert)", null);

        // Default DNAME for client as requested (only ask for name/pass)
        String cn = filename; 
        String dname = "CN=" + cn + ", OU=Hospital, O=Hospital, L=City, ST=State, C=US";

        File p12File = new File(CLIENT_DIR, filename + ".p12");
        File cerFile = new File(CLIENT_DIR, filename + ".cer");
        
        // Check if .p12 already exists
        if (p12File.exists()) {
            System.out.println("⚠️ Client file already exists: " + p12File.getPath());
            String overwrite = prompt(reader, "Overwrite? (yes/no)", "no");
            if (!"yes".equalsIgnoreCase(overwrite)) {
                System.out.println("Cancelled.");
                return;
            }
            p12File.delete();
        }

        // 1. Generate Client Keypair
        runKeytool("-genkeypair", "-alias", filename, "-keyalg", "RSA", "-keysize", "2048", 
                   "-storetype", "PKCS12", "-keystore", p12File.getAbsolutePath(), 
                   "-validity", "3650", "-storepass", clientPass, "-dname", dname);

        // 2. Export Public Cert
        runKeytool("-exportcert", "-alias", filename, "-keystore", p12File.getAbsolutePath(), 
                   "-storepass", clientPass, "-file", cerFile.getAbsolutePath());

        // 3. Delete old alias from server truststore if exists (to allow re-issuing)
        try {
            runKeytoolSilent("-delete", "-alias", filename, "-keystore", SERVER_KEYSTORE.getAbsolutePath(), 
                       "-storepass", serverPass);
        } catch (Exception ignored) {
            // Alias didn't exist, that's fine
        }

        // 4. Import into Server Truststore
        boolean importedToServer = false;
        while (true) {
            try {
                runKeytool("-importcert", "-alias", filename, "-keystore", SERVER_KEYSTORE.getAbsolutePath(), 
                           "-storepass", serverPass, "-file", cerFile.getAbsolutePath(), "-noprompt");
                importedToServer = true;
                break;
            } catch (Exception e) {
                System.out.println("❌ Import failed. Likely incorrect Server Keystore password.");
                String retry = prompt(reader, "Try again? (y/n)", "y");
                if (!"y".equalsIgnoreCase(retry)) {
                    System.out.println("⚠️ Warning: Client certificate created but NOT imported to server.");
                    break;
                }
                serverPass = prompt(reader, "Enter Server Keystore Password", null);
            }
        }
        
        // Cleanup cer file
        if (cerFile.exists()) cerFile.delete();
        
        if (!importedToServer) {
            System.out.println("❌ Client cert was NOT added to server. The .p12 remains at: " + p12File.getPath());
            return;
        }

        System.out.println("✅ Client Certificate created: " + p12File.getPath());
        System.out.println("✅ Imported into Server Truststore.");

        // Ask if this is for this machine or another device
        System.out.println("\n--- Certificate Distribution ---");
        System.out.println("1. Install on THIS machine (import now, delete .p12)");
        System.out.println("2. Send to ANOTHER device (keep .p12 for distribution)");
        String installChoice = prompt(reader, "Choose option", "2");
        
        if ("1".equals(installChoice)) {
            // Install on this machine
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                System.out.println("\n--- Importing into Windows Certificate Store (non-exportable) ---");
                boolean imported = false;
                try {
                    List<String> ps = new ArrayList<>();
                    ps.add("powershell");
                    ps.add("-NoProfile");
                    ps.add("-Command");
                    String cmd = String.format("$pwd = ConvertTo-SecureString -String '%s' -AsPlainText -Force; Import-PfxCertificate -FilePath '%s' -CertStoreLocation Cert:\\CurrentUser\\My -Password $pwd", 
                                               clientPass.replace("'", "''"), p12File.getAbsolutePath().replace("'", "''"));
                    ps.add(cmd);

                    ProcessBuilder pb = new ProcessBuilder(ps);
                    pb.inheritIO();
                    Process p = pb.start();
                    int rc = p.waitFor();
                    if (rc == 0) {
                        System.out.println("✅ Imported into Windows CurrentUser\\My as NON-EXPORTABLE.");
                        imported = true;
                    } else {
                        System.out.println("❌ PowerShell import returned exit code: " + rc);
                    }
                } catch (Exception e) {
                    System.out.println("❌ Failed to import using PowerShell: " + e.getMessage());
                }

                if (imported) {
                    if (p12File.exists() && p12File.delete()) {
                        System.out.println("🗑️ Deleted .p12 file for security.");
                    }
                    System.out.println("\n✅ DONE! Certificate installed and cannot be exported.");
                } else {
                    System.out.println("⚠️ Import failed. The .p12 remains at: " + p12File.getPath());
                }
            } else {
                System.out.println("⚠️ Auto-import only works on Windows. File kept at: " + p12File.getPath());
            }
        } else {
            // Keep .p12 for distribution
            System.out.println("\n" + "=".repeat(60));
            System.out.println("📦 CLIENT CERTIFICATE READY FOR DISTRIBUTION");
            System.out.println("=".repeat(60));
            System.out.println("File: " + p12File.getAbsolutePath());
            System.out.println("Password: " + clientPass);
            System.out.println();
            System.out.println("📋 INSTALLATION INSTRUCTIONS FOR TARGET DEVICE:");
            System.out.println("-".repeat(60));
            System.out.println();
            System.out.println("▶ WINDOWS (Chrome/Edge) - RECOMMENDED METHOD:");
            System.out.println("  1. Copy .p12 file to target device");
            System.out.println("  2. Open PowerShell as Administrator and run:");
            System.out.println("     $pwd = ConvertTo-SecureString -String '" + clientPass + "' -AsPlainText -Force");
            System.out.println("     Import-PfxCertificate -FilePath 'C:\\path\\to\\" + filename + ".p12' -CertStoreLocation Cert:\\CurrentUser\\My -Password $pwd");
            System.out.println("  3. Delete the .p12 file from the device");
            System.out.println("  ⚠️ Do NOT use double-click import (allows export)!");
            System.out.println();
            System.out.println("▶ WINDOWS (Manual - less secure):");
            System.out.println("  1. Double-click .p12 → Current User → Next");
            System.out.println("  2. Enter password: " + clientPass);
            System.out.println("  3. ⚠️ UNCHECK 'Mark this key as exportable'");
            System.out.println("  4. Finish, then DELETE the .p12 file");
            System.out.println();
            System.out.println("▶ macOS:");
            System.out.println("  1. Double-click .p12 → Enter password");
            System.out.println("  2. In Keychain Access, right-click cert → Get Info");
            System.out.println("  3. Expand 'Access Control' → Select 'Confirm before allowing access'");
            System.out.println("  4. Delete the .p12 file");
            System.out.println();
            System.out.println("▶ iOS/iPadOS:");
            System.out.println("  1. Send .p12 via AirDrop/email → Open it");
            System.out.println("  2. Settings → General → VPN & Device Management → Install");
            System.out.println("  3. Enter password: " + clientPass);
            System.out.println("  Note: iOS does not allow key export by default ✓");
            System.out.println();
            System.out.println("▶ Android:");
            System.out.println("  1. Transfer .p12 to device");
            System.out.println("  2. Settings → Security → Install certificate → VPN & app user certificate");
            System.out.println("  3. Enter password: " + clientPass);
            System.out.println("  Note: Android does not allow key export by default ✓");
            System.out.println();
            System.out.println("=".repeat(60));
            System.out.println("🔒 SECURITY: After installing on target device, DELETE the .p12 file!");
            System.out.println("=".repeat(60));
        }
    }

    private static String prompt(BufferedReader reader, String message, String defaultValue) throws IOException {
        while (true) {
            if (defaultValue != null) {
                System.out.print(message + " [default: " + defaultValue + "]: ");
            } else {
                System.out.print(message + ": ");
            }
            
            String input = reader.readLine();
            if (input != null && !input.trim().isEmpty()) {
                return input.trim();
            }
            
            if (defaultValue != null) {
                return defaultValue;
            }
            System.out.println("❌ Value is required. Please try again.");
        }
    }

    private static void listClientCerts(BufferedReader reader) throws Exception {
        if (!SERVER_KEYSTORE.exists()) {
            System.out.println("❌ Server keystore not found. Run Option 1 first.");
            return;
        }
        
        String serverPass = prompt(reader, "Enter Server Keystore Password", null);
        
        System.out.println("\n--- Certificates in Server Keystore ---");
        System.out.println("Note: 'server' is the server's own certificate (always present).");
        System.out.println("      Other entries are trusted CLIENT certificates.\n");
        
        try {
            List<String> command = new ArrayList<>();
            command.add("keytool");
            command.add("-list");
            command.add("-v");  // verbose to show more details
            command.add("-keystore");
            command.add(SERVER_KEYSTORE.getAbsolutePath());
            command.add("-storepass");
            command.add(serverPass);
            
            ProcessBuilder pb = new ProcessBuilder(command);
            // Capture output to filter and format it
            pb.redirectErrorStream(true);
            Process p = pb.start();
            
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            int clientCount = 0;
            boolean inServerEntry = false;
            
            while ((line = br.readLine()) != null) {
                // Check if we're entering a new alias
                if (line.startsWith("Alias name:")) {
                    String alias = line.substring("Alias name:".length()).trim();
                    if ("server".equalsIgnoreCase(alias)) {
                        inServerEntry = true;
                        System.out.println("📌 " + line + " (SERVER - this is the server's identity)");
                    } else {
                        inServerEntry = false;
                        clientCount++;
                        System.out.println("👤 " + line + " (CLIENT #" + clientCount + ")");
                    }
                } else if (line.contains("Entry type:") || line.contains("Owner:") || line.contains("Valid from:")) {
                    // Show key info lines
                    System.out.println("   " + line);
                }
            }
            p.waitFor();
            
            System.out.println("\n----------------------------------");
            System.out.println("Total client certificates: " + clientCount);
            
        } catch (Exception e) {
            System.out.println("❌ Failed to list certificates: " + e.getMessage());
        }
    }
    
    private static void runKeytool(String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("keytool");
        for (String arg : args) command.add(arg);
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();
        Process p = pb.start();
        int exitCode = p.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code: " + exitCode);
        }
    }
    
    private static void runKeytoolSilent(String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("keytool");
        for (String arg : args) command.add(arg);
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        // Consume output silently
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            while (br.readLine() != null) { }
        }
        int exitCode = p.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code: " + exitCode);
        }
    }

    private static void cleanupBin() {
        File binCerts = new File("bin/certs");
        File binClients = new File("bin/clients");
        deleteDir(binCerts);
        deleteDir(binClients);
    }

    private static void deleteDir(File file) {
        if (file.exists()) {
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                if (files != null) {
                    for (File f : files) deleteDir(f);
                }
            }
            file.delete();
        }
    }
}
