package service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class KeyService {

    public static final String DOCTOR_PUBLIC_KEY = "keys/doctor/public.key";
    public static final String DOCTOR_PRIVATE_KEY = "keys/doctor/private.key";
    public static final String NURSE_PUBLIC_KEY = "keys/nurse/public.key";
    public static final String NURSE_PRIVATE_KEY = "keys/nurse/private.key";

    public PublicKey loadPublicKey(String pathStr) throws Exception {
        Path path = Paths.get(pathStr);
        if (!Files.exists(path)) {
            throw new Exception("Key not found at " + path.toAbsolutePath());
        }
        
        String keyContent = Files.readString(path);
        String publicKeyPEM = keyContent
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = java.util.Base64.getDecoder().decode(publicKeyPEM);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }

    public PrivateKey loadPrivateKey(String pathStr) throws Exception {
        Path path = Paths.get(pathStr);
        if (!Files.exists(path)) {
            throw new Exception("Key not found at " + path.toAbsolutePath());
        }

        String keyContent = Files.readString(path);
        String privateKeyPEM = keyContent
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = java.util.Base64.getDecoder().decode(privateKeyPEM);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }
}
