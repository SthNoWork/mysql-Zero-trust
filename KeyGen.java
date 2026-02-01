import java.io.FileOutputStream;
import java.io.FileWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.io.File;

/**
 * RSA Key Generator for E2EE Hospital System
 * 
 * Generates RSA-2048 key pairs for:
 * - Doctor (keys/doctor/private.key, keys/doctor/public.key)
 * - Nurse (keys/nurse/private.key, keys/nurse/public.key)
 * 
 * Keys are in PEM format compatible with Web Crypto API.
 */
public class KeyGen {

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║        E2EE Hospital System - Key Generator                 ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Create directories
        new File("keys/doctor").mkdirs();
        new File("keys/nurse").mkdirs();

        // Generate Doctor keys
        System.out.println("🔑 Generating Doctor RSA-2048 key pair...");
        generateKeyPair("keys/doctor");
        System.out.println("   ✅ keys/doctor/private.key");
        System.out.println("   ✅ keys/doctor/public.key");

        // Generate Nurse keys
        System.out.println("\n🔑 Generating Nurse RSA-2048 key pair...");
        generateKeyPair("keys/nurse");
        System.out.println("   ✅ keys/nurse/private.key");
        System.out.println("   ✅ keys/nurse/public.key");

        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                    Keys Generated!                           ║");
        System.out.println("║                                                              ║");
        System.out.println("║  ⚠️  IMPORTANT: Keep private.key files SECRET!               ║");
        System.out.println("║      - Never upload to server                                ║");
        System.out.println("║      - Never share with others                               ║");
        System.out.println("║      - Backup securely                                       ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }

    private static void generateKeyPair(String directory) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();

        // Write private key in PKCS#8 PEM format
        writePEM(directory + "/private.key", "PRIVATE KEY", pair.getPrivate().getEncoded());

        // Write public key in X.509 PEM format
        writePEM(directory + "/public.key", "PUBLIC KEY", pair.getPublic().getEncoded());
    }

    private static void writePEM(String path, String type, byte[] key) throws Exception {
        String base64 = Base64.getEncoder().encodeToString(key);
        
        // Format with 64-character lines
        StringBuilder formatted = new StringBuilder();
        formatted.append("-----BEGIN ").append(type).append("-----\n");
        for (int i = 0; i < base64.length(); i += 64) {
            formatted.append(base64, i, Math.min(i + 64, base64.length()));
            formatted.append("\n");
        }
        formatted.append("-----END ").append(type).append("-----\n");

        try (FileWriter fw = new FileWriter(path)) {
            fw.write(formatted.toString());
        }
    }
}
