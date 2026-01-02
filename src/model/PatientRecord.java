package model;

import java.sql.Date;
import java.sql.Timestamp;

public class PatientRecord {
    private int recordIndex;
    private String patientId; // Plaintext ID for input
    private String patientIdHash;
    private String patientName;
    private Date patientDob;
    private String doctorName;
    private String nurseName;
    private Timestamp checkInDate;
    
    // Encrypted Data
    private byte[] encryptedSymptoms;
    private byte[] encryptedDiagnosis;
    private byte[] encryptedImages;
    private byte[] encryptedVideos;
    private byte[] doctorEncryptedAesKey;
    private byte[] nurseEncryptedAesKey;

    // Getters and Setters
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

    public byte[] getEncryptedSymptoms() { return encryptedSymptoms; }
    public void setEncryptedSymptoms(byte[] encryptedSymptoms) { this.encryptedSymptoms = encryptedSymptoms; }

    public byte[] getEncryptedDiagnosis() { return encryptedDiagnosis; }
    public void setEncryptedDiagnosis(byte[] encryptedDiagnosis) { this.encryptedDiagnosis = encryptedDiagnosis; }

    public byte[] getEncryptedImages() { return encryptedImages; }
    public void setEncryptedImages(byte[] encryptedImages) { this.encryptedImages = encryptedImages; }

    public byte[] getEncryptedVideos() { return encryptedVideos; }
    public void setEncryptedVideos(byte[] encryptedVideos) { this.encryptedVideos = encryptedVideos; }

    public byte[] getDoctorEncryptedAesKey() { return doctorEncryptedAesKey; }
    public void setDoctorEncryptedAesKey(byte[] doctorEncryptedAesKey) { this.doctorEncryptedAesKey = doctorEncryptedAesKey; }

    public byte[] getNurseEncryptedAesKey() { return nurseEncryptedAesKey; }
    public void setNurseEncryptedAesKey(byte[] nurseEncryptedAesKey) { this.nurseEncryptedAesKey = nurseEncryptedAesKey; }
}
