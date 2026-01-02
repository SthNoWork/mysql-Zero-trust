package service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.Arrays;

public class Decryptor {

    private final PrivateKey rsaPrivateKey;
    private static final int GCM_IV_SIZE = 12;
    private static final int GCM_TAG_SIZE = 128;

    public Decryptor(PrivateKey rsaPrivateKey) {
        this.rsaPrivateKey = rsaPrivateKey;
    }

    // 1. Decrypt the AES Key using RSA Private Key
    public SecretKey decryptAESKey(byte[] encryptedAesKey) throws Exception {
        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsa.init(Cipher.DECRYPT_MODE, rsaPrivateKey);
        byte[] aesKeyBytes = rsa.doFinal(encryptedAesKey);
        return new SecretKeySpec(aesKeyBytes, "AES");
    }

    // 2. Decrypt Data (String) using AES Key
    public String decryptString(byte[] encryptedDataWithIv, SecretKey aesKey) throws Exception {
        byte[] decryptedBytes = decryptBytes(encryptedDataWithIv, aesKey);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    // 3. Decrypt Data (Bytes) using AES Key
    public byte[] decryptBytes(byte[] encryptedDataWithIv, SecretKey aesKey) throws Exception {
        // Extract IV
        byte[] iv = Arrays.copyOfRange(encryptedDataWithIv, 0, GCM_IV_SIZE);
        // Extract Encrypted Data
        byte[] encryptedBytes = Arrays.copyOfRange(encryptedDataWithIv, GCM_IV_SIZE, encryptedDataWithIv.length);

        // AES-GCM Decrypt
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_SIZE, iv));
        return aes.doFinal(encryptedBytes);
    }
}
