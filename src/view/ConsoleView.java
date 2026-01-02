package view;

import model.PatientRecord;
import java.sql.Date;
import java.util.List;
import java.util.Scanner;

public class ConsoleView {

    private final Scanner scanner;

    public ConsoleView() {
        this.scanner = new Scanner(System.in);
    }

    public void close() {
        scanner.close();
    }

    public String[] getDatabaseCredentials() {
        System.out.println("--- Database Login ---");
        System.out.print("Enter MySQL username: ");
        String user = scanner.nextLine();
        System.out.print("Enter MySQL password: ");
        String pass = scanner.nextLine();
        return new String[]{user, pass};
    }

    public String showMainMenu() {
        System.out.println("\n--- Hospital System ---");
        System.out.println("1. Insert New Record");
        System.out.println("2. Search Record");
        System.out.println("3. Update Record");
        System.out.println("4. Exit");
        System.out.print("Choose option: ");
        return scanner.nextLine();
    }

    public void showMessage(String message) {
        System.out.println(message);
    }

    public PatientRecord getPatientInput() {
        System.out.println("\n--- Enter Hospital Record Details ---");
        PatientRecord record = new PatientRecord();

        System.out.print("Patient ID: ");
        record.setPatientId(scanner.nextLine());

        fillRecordDetails(record);
        return record;
    }

    public void fillRecordDetails(PatientRecord record) {
        System.out.print("Patient Name: ");
        record.setPatientName(scanner.nextLine());

        System.out.print("Patient DOB (yyyy-mm-dd): ");
        try {
            record.setPatientDob(Date.valueOf(scanner.nextLine()));
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid date format. Using current date as fallback.");
            record.setPatientDob(new Date(System.currentTimeMillis()));
        }

        System.out.print("Doctor Name: ");
        record.setDoctorName(scanner.nextLine());

        System.out.print("Nurse Name: ");
        record.setNurseName(scanner.nextLine());
    }

    public String[] getMedicalDataInput() {
        System.out.print("Symptoms: ");
        String symptoms = scanner.nextLine();
        System.out.print("Diagnosis: ");
        String diagnosis = scanner.nextLine();
        return new String[]{symptoms, diagnosis};
    }

    public boolean isDoctor() {
        System.out.println("Who are you?");
        System.out.println("1. Doctor");
        System.out.println("2. Nurse");
        System.out.print("Choose role: ");
        return scanner.nextLine().equals("1");
    }

    public String[] getSearchQuery() {
        System.out.println("Search by:");
        System.out.println("1. Patient ID");
        System.out.println("2. Patient Name");
        System.out.println("3. Date of Birth (yyyy-mm-dd)");
        System.out.print("Choose option: ");
        String option = scanner.nextLine();

        String type = "";
        String query = "";

        if (option.equals("1")) {
            type = "id";
            System.out.print("Enter Patient ID: ");
            query = scanner.nextLine();
        } else if (option.equals("2")) {
            type = "name";
            System.out.print("Enter Patient Name: ");
            query = scanner.nextLine();
        } else if (option.equals("3")) {
            type = "dob";
            System.out.print("Enter DOB: ");
            query = scanner.nextLine();
        } else {
            return null;
        }
        return new String[]{query, type};
    }

    public PatientRecord selectRecord(List<PatientRecord> results) {
        if (results.isEmpty()) {
            System.out.println("No records found.");
            return null;
        }

        System.out.println("\n--- Found Records ---");
        for (int i = 0; i < results.size(); i++) {
            PatientRecord r = results.get(i);
            System.out.printf("%d. Name: %s | DOB: %s | Doc: %s | Nurse: %s | Check-in: %s\n", 
                    (i + 1), r.getPatientName(), r.getPatientDob(), r.getDoctorName(), r.getNurseName(), r.getCheckInDate());
        }

        System.out.print("\nEnter the number of the record to select (1-" + results.size() + "): ");
        try {
            int selection = Integer.parseInt(scanner.nextLine());
            if (selection < 1 || selection > results.size()) {
                System.out.println("Invalid selection.");
                return null;
            }
            return results.get(selection - 1);
        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
            return null;
        }
    }

    public void displayDecryptedData(String symptoms, String diagnosis) {
        System.out.println("\n--- Decrypted Data ---");
        System.out.println("Symptoms: " + symptoms);
        System.out.println("Diagnosis: " + diagnosis);
    }
}
