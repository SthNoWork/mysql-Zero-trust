package repository;

import model.PatientRecord;
import java.sql.SQLException;
import java.util.List;

public interface HospitalRepository {
    void insert(PatientRecord record) throws SQLException;
    void update(PatientRecord record) throws SQLException;
    List<PatientRecord> search(String query, String type) throws SQLException;
    PatientRecord getById(int recordIndex) throws SQLException;
}
