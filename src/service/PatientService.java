package service;

import model.PatientRecord;
import javax.crypto.SecretKey;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * @deprecated Server-side encryption is removed in E2EE architecture.
 * All encryption/decryption now happens CLIENT-SIDE only.
 * Kept for backwards compatibility with console app (Main.java).
 */
@Deprecated
public class PatientService {

    private final KeyService keyService;
    private final MediaService mediaService;

    public PatientService() {
        this.keyService = new KeyService();
        this.mediaService = new MediaService();
    }

    public MediaService.MediaResult processEncryption(PatientRecord record, String symptoms, String diagnosis) throws Exception {
        PublicKey doctorKey = keyService.loadPublicKey(KeyService.DOCTOR_PUBLIC_KEY);
        PublicKey nurseKey = keyService.loadPublicKey(KeyService.NURSE_PUBLIC_KEY);

        Encryptor doctorEncryptor = new Encryptor(doctorKey);
        Encryptor nurseEncryptor = new Encryptor(nurseKey);

        SecretKey aesKey = doctorEncryptor.generateAESKey();
        record.setEncryptedSymptoms(Base64.getEncoder().encodeToString(doctorEncryptor.encryptWithAES(symptoms, aesKey)));
        record.setEncryptedDiagnosis(Base64.getEncoder().encodeToString(doctorEncryptor.encryptWithAES(diagnosis, aesKey)));

        MediaService.MediaResult mediaResult = mediaService.processMediaFiles(doctorEncryptor, aesKey);
        record.setEncryptedImages(Base64.getEncoder().encodeToString(mediaResult.imageBytes));
        record.setEncryptedVideos(Base64.getEncoder().encodeToString(mediaResult.videoBytes));

        record.setDoctorEncryptedAesKey(Base64.getEncoder().encodeToString(doctorEncryptor.encryptAESKeyWithRSA(aesKey)));
        record.setNurseEncryptedAesKey(Base64.getEncoder().encodeToString(nurseEncryptor.encryptAESKeyWithRSA(aesKey)));

        return mediaResult;
    }

    public void decryptAndRestore(PatientRecord record, boolean isDoctor) throws Exception {
        String keyPath = isDoctor ? KeyService.DOCTOR_PRIVATE_KEY : KeyService.NURSE_PRIVATE_KEY;
        PrivateKey privateKey = keyService.loadPrivateKey(keyPath);

        Decryptor decryptor = new Decryptor(privateKey);
        byte[] encryptedAesKey = Base64.getDecoder().decode(isDoctor ? record.getDoctorEncryptedAesKey() : record.getNurseEncryptedAesKey());

        if (encryptedAesKey == null || encryptedAesKey.length == 0) {
            throw new Exception("No encrypted key found for this user role.");
        }

        SecretKey aesKey = decryptor.decryptAESKey(encryptedAesKey);
        byte[] imgBytes = record.getEncryptedImages() != null ? Base64.getDecoder().decode(record.getEncryptedImages()) : new byte[0];
        byte[] vidBytes = record.getEncryptedVideos() != null ? Base64.getDecoder().decode(record.getEncryptedVideos()) : new byte[0];
        mediaService.restoreMedia(record.getRecordIndex(), imgBytes, vidBytes, decryptor, aesKey);
    }

    public String[] decryptMedicalData(PatientRecord record, boolean isDoctor) throws Exception {
        String keyPath = isDoctor ? KeyService.DOCTOR_PRIVATE_KEY : KeyService.NURSE_PRIVATE_KEY;
        PrivateKey privateKey = keyService.loadPrivateKey(keyPath);

        Decryptor decryptor = new Decryptor(privateKey);
        byte[] encryptedAesKey = Base64.getDecoder().decode(isDoctor ? record.getDoctorEncryptedAesKey() : record.getNurseEncryptedAesKey());

        if (encryptedAesKey == null || encryptedAesKey.length == 0) {
            throw new Exception("No encrypted key found for this user role.");
        }

        SecretKey aesKey = decryptor.decryptAESKey(encryptedAesKey);
        String symptoms = decryptor.decryptString(Base64.getDecoder().decode(record.getEncryptedSymptoms()), aesKey);
        String diagnosis = decryptor.decryptString(Base64.getDecoder().decode(record.getEncryptedDiagnosis()), aesKey);
        
        return new String[]{symptoms, diagnosis};
    }

    public Map<String, String> getDecryptedMedia(PatientRecord record, boolean isDoctor) throws Exception {
        String keyPath = isDoctor ? KeyService.DOCTOR_PRIVATE_KEY : KeyService.NURSE_PRIVATE_KEY;
        PrivateKey privateKey = keyService.loadPrivateKey(keyPath);

        Decryptor decryptor = new Decryptor(privateKey);
        byte[] encryptedAesKey = Base64.getDecoder().decode(isDoctor ? record.getDoctorEncryptedAesKey() : record.getNurseEncryptedAesKey());

        if (encryptedAesKey == null || encryptedAesKey.length == 0) {
            return new HashMap<>();
        }

        SecretKey aesKey = decryptor.decryptAESKey(encryptedAesKey);
        Map<String, String> media = new HashMap<>();

        if (record.getEncryptedImages() != null && !record.getEncryptedImages().isEmpty()) {
            byte[] imgBytes = mediaService.decryptImageToBytes(Base64.getDecoder().decode(record.getEncryptedImages()), decryptor, aesKey);
            if (imgBytes != null) media.put("image", Base64.getEncoder().encodeToString(imgBytes));
        }

        if (record.getEncryptedVideos() != null && !record.getEncryptedVideos().isEmpty()) {
            byte[] vidBytes = mediaService.decryptVideoToBytes(Base64.getDecoder().decode(record.getEncryptedVideos()), decryptor, aesKey);
            if (vidBytes != null) media.put("video", Base64.getEncoder().encodeToString(vidBytes));
        }

        return media;
    }
    
    public MediaService getMediaService() {
        return mediaService;
    }
}
