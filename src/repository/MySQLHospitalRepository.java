package repository;

import model.PatientRecord;
import util.DatabaseFactory;
import util.Hashing;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository implementation for E2EE hospital records.
 * Supports both MySQL and PostgreSQL (Supabase) via DatabaseFactory.
 * 
 * SECURITY: This repository NEVER decrypts medical data.
 * All encryption/decryption happens on the CLIENT side only.
 */
public class MySQLHospitalRepository implements HospitalRepository {

    @Override
    public void insert(PatientRecord record) throws SQLException {
        String tableName = DatabaseFactory.getTableName();
        String sql = "INSERT INTO " + tableName + " " +
            "(patient_id_hash, patient_name, patient_dob, check_in_date, doctor_name, nurse_name, " +
            "created_by, created_by_role, allowed_roles, recipient_ids, " +
            "encrypted_symptoms, encrypted_diagnosis, encrypted_images, encrypted_videos, " +
            "doctor_encrypted_aes_key, nurse_encrypted_aes_key) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, Hashing.sha256(record.getPatientId()));
            stmt.setString(2, record.getPatientName());
            stmt.setDate(3, record.getPatientDob());
            stmt.setTimestamp(4, record.getCheckInDate());
            stmt.setString(5, record.getDoctorName());
            stmt.setString(6, record.getNurseName());
            stmt.setString(7, record.getCreatedBy());
            stmt.setString(8, record.getCreatedByRole());
            stmt.setString(9, record.getAllowedRoles() != null ? record.getAllowedRoles() : "doctor,nurse");
            stmt.setString(10, record.getRecipientIds());
            
            // Encrypted data stored as Base64 strings (TEXT columns)
            stmt.setString(11, record.getEncryptedSymptoms());
            stmt.setString(12, record.getEncryptedDiagnosis());
            stmt.setString(13, record.getEncryptedImages());
            stmt.setString(14, record.getEncryptedVideos());
            stmt.setString(15, record.getDoctorEncryptedAesKey());
            stmt.setString(16, record.getNurseEncryptedAesKey());

            stmt.executeUpdate();
        }
    }

    @Override
    public void update(PatientRecord record) throws SQLException {
        String tableName = DatabaseFactory.getTableName();
        String sql = "UPDATE " + tableName + " SET " +
            "patient_name = ?, patient_dob = ?, check_in_date = ?, " +
            "doctor_name = ?, nurse_name = ?, allowed_roles = ?, recipient_ids = ?, " +
            "encrypted_symptoms = ?, encrypted_diagnosis = ?, " +
            "encrypted_images = ?, encrypted_videos = ?, " +
            "doctor_encrypted_aes_key = ?, nurse_encrypted_aes_key = ? " +
            "WHERE record_index = ?";

        try (Connection conn = DatabaseFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, record.getPatientName());
            stmt.setDate(2, record.getPatientDob());
            stmt.setTimestamp(3, record.getCheckInDate());
            stmt.setString(4, record.getDoctorName());
            stmt.setString(5, record.getNurseName());
            stmt.setString(6, record.getAllowedRoles());
            stmt.setString(7, record.getRecipientIds());
            stmt.setString(8, record.getEncryptedSymptoms());
            stmt.setString(9, record.getEncryptedDiagnosis());
            stmt.setString(10, record.getEncryptedImages());
            stmt.setString(11, record.getEncryptedVideos());
            stmt.setString(12, record.getDoctorEncryptedAesKey());
            stmt.setString(13, record.getNurseEncryptedAesKey());
            stmt.setInt(14, record.getRecordIndex());

            stmt.executeUpdate();
        }
    }

    @Override
    public List<PatientRecord> search(String query, String type) throws SQLException {
        return searchWithRBAC(query, type, null, null);
    }

    @Override
    public List<PatientRecord> searchWithRBAC(String query, String type, String userRole, String userId) throws SQLException {
        String tableName = DatabaseFactory.getTableName();
        boolean isPostgres = DatabaseFactory.isPostgreSQL();
        
        StringBuilder sqlBuilder = new StringBuilder("SELECT * FROM " + tableName + " WHERE ");
        
        // Build search condition based on type
        if ("id".equals(type)) {
            sqlBuilder.append("patient_id_hash = ?");
        } else if ("name".equals(type)) {
            if (isPostgres) {
                sqlBuilder.append("patient_name ILIKE ?");
            } else {
                sqlBuilder.append("patient_name LIKE ?");
            }
        } else if ("dob".equals(type)) {
            if (isPostgres) {
                sqlBuilder.append("CAST(patient_dob AS VARCHAR) LIKE ?");
            } else {
                sqlBuilder.append("CAST(patient_dob AS CHAR) LIKE ?");
            }
        }
        
        // Add RBAC filtering if role is provided
        if (userRole != null && !userRole.isEmpty()) {
            if (isPostgres) {
                sqlBuilder.append(" AND (allowed_roles ILIKE ? OR recipient_ids ILIKE ?)");
            } else {
                sqlBuilder.append(" AND (allowed_roles LIKE ? OR recipient_ids LIKE ?)");
            }
        }
        
        // Add ordering for name search
        if ("name".equals(type)) {
            if (isPostgres) {
                sqlBuilder.append(" ORDER BY CASE WHEN patient_name = ? THEN 0 ELSE 1 END, patient_name");
            } else {
                sqlBuilder.append(" ORDER BY CASE WHEN patient_name = ? THEN 0 ELSE 1 END, patient_name");
            }
        }

        List<PatientRecord> results = new ArrayList<>();
        try (Connection conn = DatabaseFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlBuilder.toString())) {

            int paramIndex = 1;
            
            if ("id".equals(type)) {
                stmt.setString(paramIndex++, Hashing.sha256(query));
            } else if ("name".equals(type)) {
                stmt.setString(paramIndex++, "%" + query + "%");
            } else if ("dob".equals(type)) {
                stmt.setString(paramIndex++, "%" + query + "%");
            }
            
            // RBAC parameters
            if (userRole != null && !userRole.isEmpty()) {
                stmt.setString(paramIndex++, "%" + userRole + "%");
                stmt.setString(paramIndex++, "%" + (userId != null ? userId : "") + "%");
            }
            
            // Order parameter for name search
            if ("name".equals(type)) {
                stmt.setString(paramIndex++, query);
            }

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                results.add(mapResultSetToRecord(rs));
            }
        }
        return results;
    }

    @Override
    public PatientRecord getById(int recordIndex) throws SQLException {
        String tableName = DatabaseFactory.getTableName();
        String sql = "SELECT * FROM " + tableName + " WHERE record_index = ?";
        
        try (Connection conn = DatabaseFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, recordIndex);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToRecord(rs);
            }
        }
        return null;
    }

    private PatientRecord mapResultSetToRecord(ResultSet rs) throws SQLException {
        PatientRecord record = new PatientRecord();
        record.setRecordIndex(rs.getInt("record_index"));
        record.setPatientName(rs.getString("patient_name"));
        record.setPatientDob(rs.getDate("patient_dob"));
        record.setDoctorName(rs.getString("doctor_name"));
        record.setNurseName(rs.getString("nurse_name"));
        record.setCheckInDate(rs.getTimestamp("check_in_date"));
        
        // RBAC metadata
        record.setCreatedBy(rs.getString("created_by"));
        record.setCreatedByRole(rs.getString("created_by_role"));
        record.setAllowedRoles(rs.getString("allowed_roles"));
        record.setRecipientIds(rs.getString("recipient_ids"));
        
        // Encrypted data (Base64 strings - NOT decrypted on server)
        record.setEncryptedSymptoms(rs.getString("encrypted_symptoms"));
        record.setEncryptedDiagnosis(rs.getString("encrypted_diagnosis"));
        record.setEncryptedImages(rs.getString("encrypted_images"));
        record.setEncryptedVideos(rs.getString("encrypted_videos"));
        record.setDoctorEncryptedAesKey(rs.getString("doctor_encrypted_aes_key"));
        record.setNurseEncryptedAesKey(rs.getString("nurse_encrypted_aes_key"));
        
        return record;
    }
}
