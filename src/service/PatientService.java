package service;

import model.PatientRecord;
import javax.crypto.SecretKey;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class PatientService {

    private final KeyService keyService;
    private final MediaService mediaService;

    public PatientService() {
        this.keyService = new KeyService();
        this.mediaService = new MediaService();
    }

    public void encryptAndPrepareRecord(PatientRecord record, String symptoms, String diagnosis) throws Exception {
        // Load Keys
        PublicKey doctorKey = keyService.loadPublicKey(KeyService.DOCTOR_PUBLIC_KEY);
        PublicKey nurseKey = keyService.loadPublicKey(KeyService.NURSE_PUBLIC_KEY);

        Encryptor doctorEncryptor = new Encryptor(doctorKey);
        Encryptor nurseEncryptor = new Encryptor(nurseKey);

        // Generate AES Key & Encrypt Data
        SecretKey aesKey = doctorEncryptor.generateAESKey();
        record.setEncryptedSymptoms(doctorEncryptor.encryptWithAES(symptoms, aesKey));
        record.setEncryptedDiagnosis(doctorEncryptor.encryptWithAES(diagnosis, aesKey));

        // Process Media
        MediaService.MediaResult mediaResult = mediaService.processMediaFiles(doctorEncryptor, aesKey);
        record.setEncryptedImages(mediaResult.imageBytes);
        record.setEncryptedVideos(mediaResult.videoBytes);
        
        record.setDoctorEncryptedAesKey(doctorEncryptor.encryptAESKeyWithRSA(aesKey));
        record.setNurseEncryptedAesKey(nurseEncryptor.encryptAESKeyWithRSA(aesKey));
    }

    public MediaService.MediaResult processEncryption(PatientRecord record, String symptoms, String diagnosis) throws Exception {
        PublicKey doctorKey = keyService.loadPublicKey(KeyService.DOCTOR_PUBLIC_KEY);
        PublicKey nurseKey = keyService.loadPublicKey(KeyService.NURSE_PUBLIC_KEY);

        Encryptor doctorEncryptor = new Encryptor(doctorKey);
        Encryptor nurseEncryptor = new Encryptor(nurseKey);

        SecretKey aesKey = doctorEncryptor.generateAESKey();
        record.setEncryptedSymptoms(doctorEncryptor.encryptWithAES(symptoms, aesKey));
        record.setEncryptedDiagnosis(doctorEncryptor.encryptWithAES(diagnosis, aesKey));

        MediaService.MediaResult mediaResult = mediaService.processMediaFiles(doctorEncryptor, aesKey);
        record.setEncryptedImages(mediaResult.imageBytes);
        record.setEncryptedVideos(mediaResult.videoBytes);

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

    public Map<String, String> getDecryptedMedia(PatientRecord record, boolean isDoctor) throws Exception {
        String keyPath = isDoctor ? KeyService.DOCTOR_PRIVATE_KEY : KeyService.NURSE_PRIVATE_KEY;
        PrivateKey privateKey = keyService.loadPrivateKey(keyPath);

        Decryptor decryptor = new Decryptor(privateKey);
        byte[] encryptedAesKey = isDoctor ? record.getDoctorEncryptedAesKey() : record.getNurseEncryptedAesKey();

        if (encryptedAesKey == null || encryptedAesKey.length == 0) {
            return new HashMap<>();
        }

        SecretKey aesKey = decryptor.decryptAESKey(encryptedAesKey);
        Map<String, String> media = new HashMap<>();

        byte[] imgBytes = mediaService.decryptImageToBytes(record.getEncryptedImages(), decryptor, aesKey);
        if (imgBytes != null) {
            media.put("image", Base64.getEncoder().encodeToString(imgBytes));
        }

        byte[] vidBytes = mediaService.decryptVideoToBytes(record.getEncryptedVideos(), decryptor, aesKey);
        if (vidBytes != null) {
            media.put("video", Base64.getEncoder().encodeToString(vidBytes));
        }

        return media;
    }
    
    public MediaService getMediaService() {
        return mediaService;
    }
}
