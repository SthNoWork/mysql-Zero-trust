package repository;

import model.PatientRecord;
import util.DBConnection;
import util.Hashing;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MySQLHospitalRepository implements HospitalRepository {

    @Override
    public void insert(PatientRecord record) throws SQLException {
        String sql = """
            INSERT INTO Hospital_Records
            (patient_id_hash, patient_name, patient_dob, check_in_date, doctor_name, nurse_name,
             encrypted_symptoms, encrypted_diagnosis, encrypted_images, encrypted_videos,
             doctor_encrypted_aes_key, nurse_encrypted_aes_key)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        Connection conn = DBConnection.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, Hashing.sha256(record.getPatientId()));
            stmt.setString(2, record.getPatientName());
            stmt.setDate(3, record.getPatientDob());
            stmt.setTimestamp(4, record.getCheckInDate());
            stmt.setString(5, record.getDoctorName());
            stmt.setString(6, record.getNurseName());
            stmt.setBytes(7, record.getEncryptedSymptoms());
            stmt.setBytes(8, record.getEncryptedDiagnosis());
            stmt.setBytes(9, record.getEncryptedImages());
            stmt.setBytes(10, record.getEncryptedVideos());
            stmt.setBytes(11, record.getDoctorEncryptedAesKey());
            stmt.setBytes(12, record.getNurseEncryptedAesKey());

            stmt.executeUpdate();
        }
    }

    @Override
    public void update(PatientRecord record) throws SQLException {
        String sql = """
            UPDATE Hospital_Records
            SET patient_name = ?,
                patient_dob = ?,
                check_in_date = ?,
                doctor_name = ?,
                nurse_name = ?,
                encrypted_symptoms = ?,
                encrypted_diagnosis = ?,
                encrypted_images = ?,
                encrypted_videos = ?,
                doctor_encrypted_aes_key = ?,
                nurse_encrypted_aes_key = ?
            WHERE record_index = ?
        """;

        Connection conn = DBConnection.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, record.getPatientName());
            stmt.setDate(2, record.getPatientDob());
            stmt.setTimestamp(3, record.getCheckInDate());
            stmt.setString(4, record.getDoctorName());
            stmt.setString(5, record.getNurseName());
            stmt.setBytes(6, record.getEncryptedSymptoms());
            stmt.setBytes(7, record.getEncryptedDiagnosis());
            stmt.setBytes(8, record.getEncryptedImages());
            stmt.setBytes(9, record.getEncryptedVideos());
            stmt.setBytes(10, record.getDoctorEncryptedAesKey());
            stmt.setBytes(11, record.getNurseEncryptedAesKey());
            stmt.setInt(12, record.getRecordIndex());

            stmt.executeUpdate();
        }
    }

    @Override
    public List<PatientRecord> search(String query, String type) throws SQLException {
        String sql = "";
        if (type.equals("id")) {
            // ID is hashed, so we must search for the exact hash
            sql = "SELECT record_index, patient_id_hash, patient_name, patient_dob, check_in_date, doctor_name, nurse_name, encrypted_symptoms, encrypted_diagnosis, doctor_encrypted_aes_key, nurse_encrypted_aes_key FROM Hospital_Records WHERE patient_id_hash = ?";
        } else if (type.equals("name")) {
            // Use LIKE for partial matches, sort exact matches to the top
            sql = "SELECT record_index, patient_id_hash, patient_name, patient_dob, check_in_date, doctor_name, nurse_name, encrypted_symptoms, encrypted_diagnosis, doctor_encrypted_aes_key, nurse_encrypted_aes_key FROM Hospital_Records WHERE patient_name LIKE ? ORDER BY CASE WHEN patient_name = ? THEN 0 ELSE 1 END, patient_name";
        } else if (type.equals("dob")) {
            // Cast DATE to CHAR to allow partial search (e.g. "2000" finds all dates in 2000)
            sql = "SELECT record_index, patient_id_hash, patient_name, patient_dob, check_in_date, doctor_name, nurse_name, encrypted_symptoms, encrypted_diagnosis, doctor_encrypted_aes_key, nurse_encrypted_aes_key FROM Hospital_Records WHERE CAST(patient_dob AS CHAR) LIKE ?";
        } else {
            // Default: Search all if query is empty or type is unknown (fallback)
             sql = "SELECT record_index, patient_id_hash, patient_name, patient_dob, check_in_date, doctor_name, nurse_name, encrypted_symptoms, encrypted_diagnosis, doctor_encrypted_aes_key, nurse_encrypted_aes_key FROM Hospital_Records LIMIT 50";
        }

        List<PatientRecord> results = new ArrayList<>();
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (type.equals("id")) {
                stmt.setString(1, Hashing.sha256(query));
            } else if (type.equals("name")) {
                stmt.setString(1, "%" + query + "%");
                stmt.setString(2, query);
            } else if (type.equals("dob")) {
                stmt.setString(1, "%" + query + "%");
            }
            // No params for default case

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                results.add(mapResultSetToRecord(rs));
            }
        }
        return results;
    }

    @Override
    public PatientRecord getById(int recordIndex) throws SQLException {
        String sql = "SELECT * FROM Hospital_Records WHERE record_index = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            
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
        
        record.setEncryptedSymptoms(rs.getBytes("encrypted_symptoms"));
        record.setEncryptedDiagnosis(rs.getBytes("encrypted_diagnosis"));
        
        // Check if columns exist before accessing (for search vs getById)
        try {
            record.setEncryptedImages(rs.getBytes("encrypted_images"));
            record.setEncryptedVideos(rs.getBytes("encrypted_videos"));
        } catch (SQLException e) {
            // Columns not present in this query (search optimization)
            record.setEncryptedImages(null);
            record.setEncryptedVideos(null);
        }

        record.setDoctorEncryptedAesKey(rs.getBytes("doctor_encrypted_aes_key"));
        record.setNurseEncryptedAesKey(rs.getBytes("nurse_encrypted_aes_key"));
        
        return record;
    }
}
