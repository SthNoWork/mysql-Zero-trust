package repository;

import model.PatientRecord;
import java.sql.SQLException;
import java.util.List;

/**
 * Repository interface for E2EE hospital records.
 * Handles only encrypted data - NO decryption on server side.
 */
public interface HospitalRepository {
    
    /**
     * Insert a new encrypted patient record.
     * All medical data must already be encrypted by the client.
     */
    void insert(PatientRecord record) throws SQLException;
    
    /**
     * Update an existing encrypted patient record.
     * All medical data must already be encrypted by the client.
     */
    void update(PatientRecord record) throws SQLException;
    
    /**
     * Search records by metadata (not encrypted fields).
     * Returns encrypted records - client must decrypt.
     */
    List<PatientRecord> search(String query, String type) throws SQLException;
    
    /**
     * Get a record by its index.
     * Returns encrypted record - client must decrypt.
     */
    PatientRecord getById(int recordIndex) throws SQLException;
    
    /**
     * Search records with RBAC filtering.
     * Only returns records the user is authorized to access.
     * 
     * @param query Search query
     * @param type Search type (id, name, dob)
     * @param userRole User's role for RBAC check
     * @param userId User's ID for recipient check
     */
    List<PatientRecord> searchWithRBAC(String query, String type, String userRole, String userId) throws SQLException;
}
