import util.Hashing;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class UserSetup {

    private static final String CSV_FILE = "src/users.csv";

    public static void main(String[] args) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                System.out.println("\n--- User Management (Local CSV) ---");
                System.out.println("1. Add User");
                System.out.println("2. List Users");
                System.out.println("3. Delete User");
                System.out.println("4. Exit");
                System.out.print("Choose option: ");
                String choice = reader.readLine();

                if ("1".equals(choice)) {
                    addUser(reader);
                } else if ("2".equals(choice)) {
                    listUsers();
                } else if ("3".equals(choice)) {
                    deleteUser(reader);
                } else if ("4".equals(choice)) {
                    break;
                } else {
                    System.out.println("Invalid option.");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void addUser(BufferedReader reader) throws Exception {
        System.out.print("Enter Username: ");
        String username = reader.readLine();
        if (username == null || username.trim().isEmpty()) return;

        System.out.print("Enter Password: ");
        String password = reader.readLine();
        if (password == null || password.trim().isEmpty()) return;

        System.out.print("Enter Role (doctor/nurse): ");
        String role = reader.readLine();
        if (role == null || role.trim().isEmpty()) role = "doctor";
        role = role.toLowerCase();

        if (!role.equals("doctor") && !role.equals("nurse")) {
            System.out.println("❌ Invalid role. Must be 'doctor' or 'nurse'.");
            return;
        }

        String hash = Hashing.sha256(password);
        
        // Format: username,role,password_hash
        String line = username + "," + role + "," + hash;
        
        // Append to CSV
        try (FileWriter fw = new FileWriter(CSV_FILE, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.println(line);
        }

        System.out.println("✅ User '" + username + "' added to " + CSV_FILE);
    }

    private static void listUsers() throws Exception {
        File file = new File(CSV_FILE);
        if (!file.exists()) {
            System.out.println("No users found.");
            return;
        }

        System.out.println("\n--- Current Users ---");
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    System.out.println("User: " + parts[0] + " | Role: " + parts[1]);
                }
            }
        }
    }

    private static void deleteUser(BufferedReader reader) throws Exception {
        System.out.print("Enter Username to delete: ");
        String usernameToDelete = reader.readLine();
        if (usernameToDelete == null || usernameToDelete.trim().isEmpty()) return;

        File file = new File(CSV_FILE);
        if (!file.exists()) {
            System.out.println("No users file found.");
            return;
        }

        List<String> lines = new ArrayList<>();
        boolean found = false;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length > 0 && parts[0].equals(usernameToDelete)) {
                    found = true;
                } else {
                    lines.add(line);
                }
            }
        }

        if (found) {
            try (PrintWriter out = new PrintWriter(new FileWriter(file))) {
                for (String l : lines) {
                    out.println(l);
                }
            }
            System.out.println("✅ User '" + usernameToDelete + "' deleted.");
        } else {
            System.out.println("❌ User '" + usernameToDelete + "' not found.");
        }
    }
}
