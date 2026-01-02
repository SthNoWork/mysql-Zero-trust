package service;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;

public class Encryptor {

    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_IV_SIZE = 12;
    private static final int GCM_TAG_SIZE = 128;

    private final PublicKey rsaPublicKey;

    public Encryptor(PublicKey rsaPublicKey) {
        this.rsaPublicKey = rsaPublicKey;
    }

    // Generate a new AES Key
    public SecretKey generateAESKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(AES_KEY_SIZE);
        return keyGen.generateKey();
    }

    // Encrypt the AES Key using RSA
    public byte[] encryptAESKeyWithRSA(SecretKey aesKey) throws Exception {
        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsa.init(Cipher.ENCRYPT_MODE, rsaPublicKey);
        return rsa.doFinal(aesKey.getEncoded());
    }

    // Encrypt data using an existing AES Key
    public byte[] encryptWithAES(String plainText, SecretKey aesKey) throws Exception {
        // Generate random IV
        byte[] iv = new byte[GCM_IV_SIZE];
        SecureRandom.getInstanceStrong().nextBytes(iv);

        // AES-GCM encrypt
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_SIZE, iv));
        byte[] encryptedBytes = aes.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        // Combine IV + Encrypted Data (we need IV to decrypt!)
        // Format: [IV (12 bytes)] [Encrypted Data]
        byte[] result = new byte[GCM_IV_SIZE + encryptedBytes.length];
        System.arraycopy(iv, 0, result, 0, GCM_IV_SIZE);
        System.arraycopy(encryptedBytes, 0, result, GCM_IV_SIZE, encryptedBytes.length);
        return result;
    }

    // Encrypt raw bytes (for images/videos)
    public byte[] encryptBytesWithAES(byte[] data, SecretKey aesKey) throws Exception {
        // Generate random IV
        byte[] iv = new byte[GCM_IV_SIZE];
        SecureRandom.getInstanceStrong().nextBytes(iv);

        // AES-GCM encrypt
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_SIZE, iv));
        byte[] encryptedBytes = aes.doFinal(data);

        // Combine IV + Encrypted Data
        byte[] result = new byte[GCM_IV_SIZE + encryptedBytes.length];
        System.arraycopy(iv, 0, result, 0, GCM_IV_SIZE);
        System.arraycopy(encryptedBytes, 0, result, GCM_IV_SIZE, encryptedBytes.length);
        return result;
    }

    // Encrypt a single string (generates new key)
    public EncryptedData encrypt(String plainText) throws Exception {
        // 1️⃣ Generate AES key
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(AES_KEY_SIZE);
        SecretKey aesKey = keyGen.generateKey();

        // 2️⃣ Generate random IV
        byte[] iv = new byte[GCM_IV_SIZE];
        SecureRandom.getInstanceStrong().nextBytes(iv);

        // 3️⃣ AES-GCM encrypt
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_SIZE, iv));
        byte[] encryptedData = aes.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        // 4️⃣ Encrypt AES key with RSA
        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsa.init(Cipher.ENCRYPT_MODE, rsaPublicKey);
        byte[] encryptedAesKey = rsa.doFinal(aesKey.getEncoded());

        // 5️⃣ Return both encrypted bytes + IV + encrypted AES key
        return new EncryptedData(encryptedData, iv, encryptedAesKey);
    }

    // ================= Helper class to hold encrypted data =================
    public static class EncryptedData {
        public final byte[] data;        // AES-encrypted data
        public final byte[] iv;          // IV used for AES
        public final byte[] aesKeyEnc;   // AES key encrypted with RSA

        public EncryptedData(byte[] data, byte[] iv, byte[] aesKeyEnc) {
            this.data = data;
            this.iv = iv;
            this.aesKeyEnc = aesKeyEnc;
        }

        // Optional: store as Base64 for database storage
        public String dataBase64() { return Base64.getEncoder().encodeToString(data); }
        public String ivBase64() { return Base64.getEncoder().encodeToString(iv); }
        public String keyBase64() { return Base64.getEncoder().encodeToString(aesKeyEnc); }
    }
}
