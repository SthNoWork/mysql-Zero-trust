package service;

import model.PatientRecord;
import javax.crypto.SecretKey;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import java.nio.file.Path;
import java.util.List;
import java.util.Collections;

public class PatientService {

    private final KeyService keyService;
    private final MediaService mediaService;

    public PatientService() {
        this.keyService = new KeyService();
        this.mediaService = new MediaService();
    }

    public MediaService.MediaResult processEncryption(PatientRecord record, String symptoms, String diagnosis) throws Exception {
        return processEncryption(record, symptoms, diagnosis, Collections.emptyList());
    }

    public MediaService.MediaResult processEncryption(PatientRecord record, String symptoms, String diagnosis, List<Path> mediaFiles) throws Exception {
        PublicKey doctorKey = keyService.loadPublicKey(KeyService.DOCTOR_PUBLIC_KEY);
        PublicKey nurseKey = keyService.loadPublicKey(KeyService.NURSE_PUBLIC_KEY);

        Encryptor doctorEncryptor = new Encryptor(doctorKey);
        Encryptor nurseEncryptor = new Encryptor(nurseKey);

        SecretKey aesKey = doctorEncryptor.generateAESKey();
        record.setEncryptedSymptoms(doctorEncryptor.encryptWithAES(symptoms, aesKey));
        record.setEncryptedDiagnosis(doctorEncryptor.encryptWithAES(diagnosis, aesKey));

        MediaService.MediaResult mediaResult = mediaService.processMediaFiles(doctorEncryptor, aesKey, mediaFiles);
        record.setEncryptedImages(mediaResult.imageBytes);
        record.setEncryptedVideos(mediaResult.videoBytes);
        record.setEncryptedAudios(mediaResult.audioBytes);

        record.setDoctorEncryptedAesKey(doctorEncryptor.encryptAESKeyWithRSA(aesKey));
        record.setNurseEncryptedAesKey(nurseEncryptor.encryptAESKeyWithRSA(aesKey));

        return mediaResult;
    }

    public void decryptAndRestore(PatientRecord record, boolean isDoctor) throws Exception {
        String keyPath = isDoctor ? KeyService.DOCTOR_PRIVATE_KEY : KeyService.NURSE_PRIVATE_KEY;
        PrivateKey privateKey = keyService.loadPrivateKey(keyPath);

        Decryptor decryptor = new Decryptor(privateKey);
        byte[] encryptedAesKey = isDoctor ? record.getDoctorEncryptedAesKey() : record.getNurseEncryptedAesKey();

        if (encryptedAesKey == null || encryptedAesKey.length == 0) {
            throw new Exception("No encrypted key found for this user role.");
        }

        SecretKey aesKey = decryptor.decryptAESKey(encryptedAesKey);
        
        // Decrypt strings (we return them, or print them? Service should return data)
        // But the method signature is void. Let's change it to return a DecryptedRecord DTO?
        // Or just return the strings.
        // For now, let's just restore media here, and let the caller decrypt strings using a helper?
        // No, the service should do the work.
        
        // We can't easily modify the "record" to be decrypted since it holds encrypted bytes.
        // We will just restore media here.
        mediaService.restoreMedia(record.getRecordIndex(), record.getEncryptedImages(), record.getEncryptedVideos(), decryptor, aesKey);
    }

    public String[] decryptMedicalData(PatientRecord record, boolean isDoctor) throws Exception {
        String keyPath = isDoctor ? KeyService.DOCTOR_PRIVATE_KEY : KeyService.NURSE_PRIVATE_KEY;
        PrivateKey privateKey = keyService.loadPrivateKey(keyPath);

        Decryptor decryptor = new Decryptor(privateKey);
        byte[] encryptedAesKey = isDoctor ? record.getDoctorEncryptedAesKey() : record.getNurseEncryptedAesKey();

        if (encryptedAesKey == null || encryptedAesKey.length == 0) {
            throw new Exception("No encrypted key found for this user role.");
        }

        SecretKey aesKey = decryptor.decryptAESKey(encryptedAesKey);
        String symptoms = decryptor.decryptString(record.getEncryptedSymptoms(), aesKey);
        String diagnosis = decryptor.decryptString(record.getEncryptedDiagnosis(), aesKey);
        
        return new String[]{symptoms, diagnosis};
    }

    public Map<String, List<String>> getDecryptedMedia(PatientRecord record, boolean isDoctor) throws Exception {
        String keyPath = isDoctor ? KeyService.DOCTOR_PRIVATE_KEY : KeyService.NURSE_PRIVATE_KEY;
        PrivateKey privateKey = keyService.loadPrivateKey(keyPath);

        Decryptor decryptor = new Decryptor(privateKey);
        byte[] encryptedAesKey = isDoctor ? record.getDoctorEncryptedAesKey() : record.getNurseEncryptedAesKey();

        if (encryptedAesKey == null || encryptedAesKey.length == 0) {
            return new HashMap<>();
        }

        SecretKey aesKey = decryptor.decryptAESKey(encryptedAesKey);
        Map<String, List<String>> media = new HashMap<>();
        media.put("images", new java.util.ArrayList<>());
        media.put("videos", new java.util.ArrayList<>());
        media.put("audios", new java.util.ArrayList<>());

        Map<String, byte[]> images = mediaService.decryptMediaToMap(record.getEncryptedImages(), decryptor, aesKey);
        for (byte[] img : images.values()) {
            media.get("images").add(Base64.getEncoder().encodeToString(img));
        }

        Map<String, byte[]> videos = mediaService.decryptMediaToMap(record.getEncryptedVideos(), decryptor, aesKey);
        for (byte[] vid : videos.values()) {
            media.get("videos").add(Base64.getEncoder().encodeToString(vid));
        }

        Map<String, byte[]> audios = mediaService.decryptMediaToMap(record.getEncryptedAudios(), decryptor, aesKey);
        for (byte[] aud : audios.values()) {
            media.get("audios").add(Base64.getEncoder().encodeToString(aud));
        }

        return media;
    }
}
