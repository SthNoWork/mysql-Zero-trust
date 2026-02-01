package model;

import java.sql.Date;
import java.sql.Timestamp;

/**
 * Patient record with RBAC metadata for E2EE architecture.
 * All medical data is stored encrypted - server NEVER decrypts.
 */
public class PatientRecord {
    // ========== IDENTITY ==========
    private int recordIndex;
    private String patientIdHash;       // SHA-256 hash of patient ID
    
    // ========== METADATA (Plaintext - for RBAC) ==========
    private String patientName;         // Can be searched
    private Date patientDob;            // Can be searched
    private Timestamp checkInDate;
    private String doctorName;          // Assigned doctor
    private String nurseName;           // Assigned nurse
    private String createdBy;           // User ID who created this record
    private String createdByRole;       // Role of creator (doctor/nurse/admin)
    
    // ========== RBAC METADATA ==========
    private String allowedRoles;        // Comma-separated: "doctor,nurse"
    private String recipientIds;        // Comma-separated user IDs who can decrypt

    // ========== ENCRYPTED DATA (Ciphertext - E2EE) ==========
    private String encryptedSymptoms;       // Base64 encoded AES-GCM ciphertext
    private String encryptedDiagnosis;      // Base64 encoded AES-GCM ciphertext
    private String encryptedImages;         // Base64 encoded AES-GCM ciphertext
    private String encryptedVideos;         // Base64 encoded AES-GCM ciphertext
    
    // ========== ENCRYPTED AES KEYS (Per Recipient) ==========
    private String doctorEncryptedAesKey;   // Base64 RSA-encrypted AES key for doctor
    private String nurseEncryptedAesKey;    // Base64 RSA-encrypted AES key for nurse

    // ========== Transient field for client input ==========
    private transient String patientId;     // Raw ID (never stored, only hashed)

    // ========== GETTERS AND SETTERS ==========
    
    public int getRecordIndex() { return recordIndex; }
    public void setRecordIndex(int recordIndex) { this.recordIndex = recordIndex; }

    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }

    public String getPatientIdHash() { return patientIdHash; }
    public void setPatientIdHash(String patientIdHash) { this.patientIdHash = patientIdHash; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public Date getPatientDob() { return patientDob; }
    public void setPatientDob(Date patientDob) { this.patientDob = patientDob; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }

    public String getNurseName() { return nurseName; }
    public void setNurseName(String nurseName) { this.nurseName = nurseName; }

    public Timestamp getCheckInDate() { return checkInDate; }
    public void setCheckInDate(Timestamp checkInDate) { this.checkInDate = checkInDate; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getCreatedByRole() { return createdByRole; }
    public void setCreatedByRole(String createdByRole) { this.createdByRole = createdByRole; }

    public String getAllowedRoles() { return allowedRoles; }
    public void setAllowedRoles(String allowedRoles) { this.allowedRoles = allowedRoles; }

    public String getRecipientIds() { return recipientIds; }
    public void setRecipientIds(String recipientIds) { this.recipientIds = recipientIds; }

    // ========== ENCRYPTED FIELDS (Base64 Strings) ==========
    
    public String getEncryptedSymptoms() { return encryptedSymptoms; }
    public void setEncryptedSymptoms(String encryptedSymptoms) { this.encryptedSymptoms = encryptedSymptoms; }

    public String getEncryptedDiagnosis() { return encryptedDiagnosis; }
    public void setEncryptedDiagnosis(String encryptedDiagnosis) { this.encryptedDiagnosis = encryptedDiagnosis; }

    public String getEncryptedImages() { return encryptedImages; }
    public void setEncryptedImages(String encryptedImages) { this.encryptedImages = encryptedImages; }

    public String getEncryptedVideos() { return encryptedVideos; }
    public void setEncryptedVideos(String encryptedVideos) { this.encryptedVideos = encryptedVideos; }

    public String getDoctorEncryptedAesKey() { return doctorEncryptedAesKey; }
    public void setDoctorEncryptedAesKey(String doctorEncryptedAesKey) { this.doctorEncryptedAesKey = doctorEncryptedAesKey; }

    public String getNurseEncryptedAesKey() { return nurseEncryptedAesKey; }
    public void setNurseEncryptedAesKey(String nurseEncryptedAesKey) { this.nurseEncryptedAesKey = nurseEncryptedAesKey; }
}
