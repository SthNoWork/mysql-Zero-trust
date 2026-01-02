import model.PatientRecord;
import repository.HospitalRepository;
import repository.MySQLHospitalRepository;
import service.MediaService;
import service.PatientService;
import util.DBConnection;
import view.ConsoleView;

import java.util.List;

public class Main {

    private static final HospitalRepository repository = new MySQLHospitalRepository();
    private static final PatientService patientService = new PatientService();
    private static final ConsoleView view = new ConsoleView();

    public static void main(String[] args) {
        try {
            // 0. Authenticate Database User
            String[] dbCreds = view.getDatabaseCredentials();
            DBConnection.setCredentials(dbCreds[0], dbCreds[1]);

            while (true) {
                String option = view.showMainMenu();

                if (option.equals("1")) {
                    handleInsert();
                } else if (option.equals("2")) {
                    handleSearch();
                } else if (option.equals("3")) {
                    handleUpdate();
                } else if (option.equals("4")) {
                    view.showMessage("Exiting...");
                    break;
                } else {
                    view.showMessage("Invalid option.");
                }
            }
            view.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleInsert() throws Exception {
        view.showMessage("\n--- Enter Hospital Record Details ---");
        PatientRecord record = view.getPatientInput();
        String[] medicalData = view.getMedicalDataInput();

        MediaService.MediaResult mediaResult = patientService.processEncryption(record, medicalData[0], medicalData[1]);

        try {
            repository.insert(record);
            view.showMessage("✅ Record inserted successfully.");
            patientService.getMediaService().deleteProcessedFiles(mediaResult.processedFiles);
        } catch (Exception e) {
            view.showMessage("❌ Insert failed: " + e.getMessage());
        }
    }

    private static void handleSearch() throws Exception {
        view.showMessage("\n--- Search Record ---");
        boolean isDoctor = view.isDoctor();

        String[] searchQuery = view.getSearchQuery();
        if (searchQuery == null) {
            view.showMessage("Invalid option.");
            return;
        }

        List<PatientRecord> results = repository.search(searchQuery[0], searchQuery[1]);
        PatientRecord selectedRecord = view.selectRecord(results);
        
        if (selectedRecord == null) return;

        try {
            String[] decryptedData = patientService.decryptMedicalData(selectedRecord, isDoctor);
            view.displayDecryptedData(decryptedData[0], decryptedData[1]);
            patientService.decryptAndRestore(selectedRecord, isDoctor);
        } catch (Exception e) {
            view.showMessage("❌ Decryption failed: " + e.getMessage());
        }
    }

    private static void handleUpdate() throws Exception {
        view.showMessage("\n--- Update Record ---");
        
        // Reuse search logic? Or duplicate for now?
        // Let's duplicate the search part for simplicity or extract it if we want.
        // Since view.getSearchQuery() is reusable, we can just do:
        String[] searchQuery = view.getSearchQuery();
        if (searchQuery == null) return;

        List<PatientRecord> results = repository.search(searchQuery[0], searchQuery[1]);
        PatientRecord existingRecord = view.selectRecord(results);
        
        if (existingRecord == null) return;

        view.showMessage("\n--- Enter NEW Details (Overwriting) ---");
        
        PatientRecord newRecord = new PatientRecord();
        newRecord.setRecordIndex(existingRecord.getRecordIndex());
        newRecord.setPatientId(existingRecord.getPatientId());
        
        view.fillRecordDetails(newRecord);
        String[] medicalData = view.getMedicalDataInput();

        MediaService.MediaResult mediaResult = patientService.processEncryption(newRecord, medicalData[0], medicalData[1]);

        try {
            repository.update(newRecord);
            view.showMessage("✅ Record updated successfully.");
            patientService.getMediaService().deleteProcessedFiles(mediaResult.processedFiles);
        } catch (Exception e) {
            view.showMessage("❌ Update failed: " + e.getMessage());
        }
    }
}
