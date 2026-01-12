package server;

import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import model.PatientRecord;
import repository.HospitalRepository;
import repository.MySQLHospitalRepository;
import service.PatientService;
import util.Hashing;

import javax.net.ssl.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Set;
import java.util.Collections;
import java.security.cert.CertificateException;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleWebServer {

    private static final int PORT = 8000;
    private static final HospitalRepository repository = new MySQLHospitalRepository();
    private static final PatientService patientService = new PatientService();
    
    // Token -> "username:role"
    private static final Map<String, String> sessions = new ConcurrentHashMap<>();
    
    // Track active client certificate fingerprints (1 connection per cert)
    private static final Set<String> activeClients = Collections.newSetFromMap(new ConcurrentHashMap<>());
    
    // Token -> client cert fingerprint (to remove from activeClients on logout)
    private static final Map<String, String> tokenToCertFingerprint = new ConcurrentHashMap<>();

    public void start() throws IOException, NoSuchAlgorithmException, KeyStoreException, CertificateException, UnrecoverableKeyException, KeyManagementException {
        // Load Keystore
        char[] password = "password".toCharArray();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        FileInputStream fis = new FileInputStream("src/certs/server.p12");
        ks.load(fis, password);

        // Setup KeyManager
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, password);

        // Setup TrustManager (for mTLS)
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks);

        // Setup SSLContext
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        // Create HTTPS Server
        HttpsServer server = HttpsServer.create(new InetSocketAddress(PORT), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
            public void configure(HttpsParameters params) {
                try {
                    SSLContext c = getSSLContext();
                    SSLParameters sslParams = c.getDefaultSSLParameters();
                    sslParams.setNeedClientAuth(true); // Enforce mTLS here
                    params.setSSLParameters(sslParams);
                } catch (Exception ex) {
                    System.out.println("Failed to create HTTPS port");
                }
            }
        });

        // Serve HTML
        server.createContext("/", new StaticHandler());

        // API Endpoints
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/logout", new LogoutHandler());
        server.createContext("/api/insert", new InsertHandler());
        server.createContext("/api/search", new SearchHandler());
        server.createContext("/api/update", new UpdateHandler());
        server.createContext("/api/media", new MediaHandler());

        server.setExecutor(null); // creates a default executor
        System.out.println("Server started on https://localhost:" + PORT);
        server.start();
    }

    static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String path = t.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            
            // Prevent directory traversal
            if (path.contains("..")) {
                sendResponse(t, 403, "Forbidden");
                return;
            }

            try {
                byte[] content = Files.readAllBytes(Paths.get("src/web" + path));
                t.sendResponseHeaders(200, content.length);
                OutputStream os = t.getResponseBody();
                os.write(content);
                os.close();
            } catch (IOException e) {
                sendResponse(t, 404, "File Not Found");
            }
        }
    }

    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                // Extract client certificate fingerprint
                String certFingerprint = getClientCertFingerprint(t);
                if (certFingerprint == null) {
                    sendResponse(t, 403, "Client certificate required");
                    return;
                }
                
                // Check if this certificate already has an active session
                if (activeClients.contains(certFingerprint)) {
                    sendResponse(t, 409, "This certificate already has an active session. Only 1 connection per client is allowed.");
                    return;
                }
                
                Map<String, String> params = parseJsonBody(t.getRequestBody());
                String user = params.get("user");
                String pass = params.get("pass");

                try {
                    // Check against CSV
                    String role = checkCredentials(user, pass);
                    
                    if (role != null) {
                        String token = UUID.randomUUID().toString();
                        // Store "username:role" in the session
                        sessions.put(token, user + ":" + role);
                        
                        // Mark this certificate as active
                        activeClients.add(certFingerprint);
                        tokenToCertFingerprint.put(token, certFingerprint);
                        
                        System.out.println("[LOGIN] User: " + user + ", Cert: " + certFingerprint.substring(0, 16) + "...");
                        
                        String json = "{\"token\":\"" + token + "\", \"role\":\"" + role + "\"}";
                        t.getResponseHeaders().set("Content-Type", "application/json");
                        sendResponse(t, 200, json);
                    } else {
                        sendResponse(t, 401, "Invalid Credentials");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    sendResponse(t, 500, "Login Error: " + e.getMessage());
                }
            } else {
                sendResponse(t, 405, "Method Not Allowed");
            }
        }

        private String checkCredentials(String user, String pass) {
            File file = new File("src/users.csv");
            if (!file.exists()) return null;

            String inputHash = Hashing.sha256(pass);
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length >= 3) {
                        String csvUser = parts[0];
                        String csvRole = parts[1];
                        String csvHash = parts[2];
                        
                        if (csvUser.equals(user) && csvHash.equals(inputHash)) {
                            return csvRole;
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
    
    static class LogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                String authHeader = t.getRequestHeaders().getFirst("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    String token = authHeader.substring(7);
                    
                    // Remove from sessions
                    String sessionData = sessions.remove(token);
                    
                    // Remove certificate from active clients
                    String certFingerprint = tokenToCertFingerprint.remove(token);
                    if (certFingerprint != null) {
                        activeClients.remove(certFingerprint);
                        System.out.println("[LOGOUT] Cert: " + certFingerprint.substring(0, 16) + "...");
                    }
                    
                    sendResponse(t, 200, "Logged out");
                } else {
                    sendResponse(t, 400, "No token provided");
                }
            } else {
                sendResponse(t, 405, "Method Not Allowed");
            }
        }
    }
    
    // Extract client certificate fingerprint from SSL session
    private static String getClientCertFingerprint(HttpExchange t) {
        try {
            if (t instanceof com.sun.net.httpserver.HttpsExchange) {
                com.sun.net.httpserver.HttpsExchange httpsExchange = (com.sun.net.httpserver.HttpsExchange) t;
                SSLSession sslSession = httpsExchange.getSSLSession();
                java.security.cert.Certificate[] certs = sslSession.getPeerCertificates();
                if (certs != null && certs.length > 0 && certs[0] instanceof X509Certificate) {
                    X509Certificate clientCert = (X509Certificate) certs[0];
                    // Use SHA-256 fingerprint of the certificate
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    byte[] digest = md.digest(clientCert.getEncoded());
                    StringBuilder sb = new StringBuilder();
                    for (byte b : digest) {
                        sb.append(String.format("%02x", b));
                    }
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to get client certificate: " + e.getMessage());
        }
        return null;
    }

    private static List<Path> parseMultipart(InputStream is, String boundary, Map<String, String> params) throws IOException {
        List<Path> files = new ArrayList<>();
        // Ensure media directory exists
        Files.createDirectories(Paths.get("media"));

        // Read all bytes (simplistic approach for demo)
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[1024];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        byte[] bodyBytes = buffer.toByteArray();

        // Split by boundary
        // Note: This is a very basic parser. Production code should use Apache Commons FileUpload.
        String body = new String(bodyBytes, StandardCharsets.ISO_8859_1); // Use ISO-8859-1 to preserve byte values
        String[] parts = body.split("--" + boundary);

        for (String part : parts) {
            if (part.contains("Content-Disposition: form-data;")) {
                String[] headersAndBody = part.split("\r\n\r\n", 2);
                if (headersAndBody.length < 2) continue;

                String headers = headersAndBody[0];
                String content = headersAndBody[1];
                // Remove trailing newlines/boundary markers from content
                if (content.endsWith("\r\n")) content = content.substring(0, content.length() - 2);

                String name = extractHeaderValue(headers, "name");
                String filename = extractHeaderValue(headers, "filename");

                if (filename != null) {
                    // It's a file
                    if (!filename.isEmpty()) {
                        // Convert content back to bytes
                        byte[] fileBytes = content.getBytes(StandardCharsets.ISO_8859_1);
                        Path filePath = Paths.get("media", UUID.randomUUID().toString() + "_" + filename);
                        Files.write(filePath, fileBytes);
                        files.add(filePath);
                        System.out.println("Saved uploaded file: " + filePath);
                    }
                } else {
                    // It's a field
                    params.put(name, content);
                }
            }
        }
        return files;
    }

    private static String extractHeaderValue(String headers, String key) {
        for (String line : headers.split("\r\n")) {
            if (line.contains(key + "=")) {
                int start = line.indexOf(key + "=\"") + key.length() + 2;
                int end = line.indexOf("\"", start);
                return line.substring(start, end);
            }
        }
        return null;
    }

    static class InsertHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                try {
                    // Auth Check
                    String authHeader = t.getRequestHeaders().getFirst("Authorization");
                    String sessionData = null;
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        sessionData = sessions.get(token);
                    }

                    if (sessionData == null) {
                        sendResponse(t, 401, "Unauthorized");
                        return;
                    }

                    String[] sessionParts = sessionData.split(":");
                    String currentUsername = sessionParts[0];
                    String currentRole = sessionParts[1];

                    String contentType = t.getRequestHeaders().getFirst("Content-Type");
                    Map<String, String> params = new HashMap<>();
                    List<Path> uploadedFiles = new ArrayList<>();

                    if (contentType != null && contentType.contains("multipart/form-data")) {
                        // Handle Multipart
                        String boundary = contentType.substring(contentType.indexOf("boundary=") + 9);
                        uploadedFiles = parseMultipart(t.getRequestBody(), boundary, params);
                    } else {
                        // Handle JSON
                        params = parseJsonBody(t.getRequestBody());
                    }
                    
                    PatientRecord record = new PatientRecord();
                    record.setPatientId(params.get("patientId"));
                    record.setPatientName(params.get("patientName"));
                    record.setPatientDob(Date.valueOf(params.get("patientDob")));
                    
                    String checkInStr = params.get("checkInDate");
                    if (checkInStr != null && !checkInStr.trim().isEmpty()) {
                        if (checkInStr.length() == 10) checkInStr += " 00:00:00";
                        try {
                            record.setCheckInDate(Timestamp.valueOf(checkInStr));
                        } catch (Exception e) {
                            record.setCheckInDate(new Timestamp(System.currentTimeMillis()));
                        }
                    } else {
                        record.setCheckInDate(new Timestamp(System.currentTimeMillis()));
                    }

                    // Auto-set Doctor/Nurse based on logged-in user
                    if ("doctor".equalsIgnoreCase(currentRole)) {
                        record.setDoctorName(currentUsername);
                        record.setNurseName("N/A"); // Or leave null if DB allows
                    } else if ("nurse".equalsIgnoreCase(currentRole)) {
                        record.setNurseName(currentUsername);
                        record.setDoctorName("N/A");
                    } else {
                        // Fallback or other roles
                        record.setDoctorName(params.get("doctorName"));
                        record.setNurseName(params.get("nurseName"));
                    }

                    patientService.processEncryption(record, params.get("symptoms"), params.get("diagnosis"), uploadedFiles);
                    repository.insert(record);
                    
                    // Cleanup uploaded files
                    for (Path p : uploadedFiles) {
                        try { Files.deleteIfExists(p); } catch (Exception ignore) {}
                    }
                    
                    sendResponse(t, 200, "Inserted");
                } catch (Exception e) {
                    e.printStackTrace();
                    sendResponse(t, 500, "Error: " + e.getMessage());
                }
            }
        }

    }

    static class SearchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("GET".equals(t.getRequestMethod())) {
                try {
                    Map<String, String> queryParams = parseQueryParams(t.getRequestURI().getQuery());
                    String type = queryParams.get("type");
                    String query = queryParams.get("query");
                    
                    // Auto-detect role from session
                    String role = getRoleFromRequest(t);
                    boolean isDoctor = "doctor".equalsIgnoreCase(role);
                    
                    System.out.println("Search Request - Role detected: " + role);

                    List<PatientRecord> results = repository.search(query, type);
                    
                    // Decrypt results for display
                    List<Map<String, Object>> jsonResults = new ArrayList<>();
                    for (PatientRecord r : results) {
                        try {
                            String[] decrypted = patientService.decryptMedicalData(r, isDoctor);
                            // Skip media decryption for search results
                            
                            Map<String, Object> map = new HashMap<>();
                            map.put("patientName", r.getPatientName());
                            map.put("patientDob", r.getPatientDob().toString());
                            map.put("checkInDate", r.getCheckInDate() != null ? r.getCheckInDate().toString() : "");
                            map.put("doctorName", r.getDoctorName());
                            map.put("nurseName", r.getNurseName());
                            map.put("symptoms", decrypted[0]);
                            map.put("diagnosis", decrypted[1]);
                            map.put("recordIndex", r.getRecordIndex());
                            
                            // map.put("images", media.get("images"));
                            // map.put("videos", media.get("videos"));
                            
                            jsonResults.add(map);
                        } catch (Exception e) {
                            // Skip records we can't decrypt (wrong key/role)
                            System.out.println("Failed to decrypt record " + r.getRecordIndex());
                        }
                    }

                    String json = toJson(jsonResults);
                    t.getResponseHeaders().set("Content-Type", "application/json");
                    sendResponse(t, 200, json);
                } catch (Exception e) {
                    e.printStackTrace();
                    sendResponse(t, 500, e.getMessage());
                }
            }
        }
    }

    static class MediaHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("GET".equals(t.getRequestMethod())) {
                try {
                    Map<String, String> queryParams = parseQueryParams(t.getRequestURI().getQuery());
                    String idStr = queryParams.get("id");
                    if (idStr == null) {
                        sendResponse(t, 400, "Missing id");
                        return;
                    }
                    int id = Integer.parseInt(idStr);

                    // Auto-detect role from session
                    String role = getRoleFromRequest(t);
                    boolean isDoctor = "doctor".equalsIgnoreCase(role);

                    PatientRecord r = repository.getById(id);
                    if (r == null) {
                        sendResponse(t, 404, "Record not found");
                        return;
                    }

                    Map<String, List<String>> media = patientService.getDecryptedMedia(r, isDoctor);
                    String json = toJson(media);
                    t.getResponseHeaders().set("Content-Type", "application/json");
                    sendResponse(t, 200, json);

                } catch (Exception e) {
                    e.printStackTrace();
                    sendResponse(t, 500, e.getMessage());
                }
            } else {
                sendResponse(t, 405, "Method Not Allowed");
            }
        }
    }

    static class UpdateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                try {
                    // Auth Check
                    String authHeader = t.getRequestHeaders().getFirst("Authorization");
                    String sessionData = null;
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        sessionData = sessions.get(token);
                    }

                    if (sessionData == null) {
                        sendResponse(t, 401, "Unauthorized");
                        return;
                    }

                    String contentType = t.getRequestHeaders().getFirst("Content-Type");
                    Map<String, String> params = new HashMap<>();
                    List<Path> uploadedFiles = new ArrayList<>();

                    if (contentType != null && contentType.contains("multipart/form-data")) {
                        String boundary = contentType.substring(contentType.indexOf("boundary=") + 9);
                        uploadedFiles = parseMultipart(t.getRequestBody(), boundary, params);
                    } else {
                        params = parseJsonBody(t.getRequestBody());
                    }
                    
                    int recordIndex = Integer.parseInt(params.get("recordIndex"));
                    PatientRecord existing = repository.getById(recordIndex);
                    
                    if (existing == null) {
                        sendResponse(t, 404, "Record Not Found");
                        return;
                    }

                    // Update allowed fields
                    if (params.containsKey("patientName")) existing.setPatientName(params.get("patientName"));
                    if (params.containsKey("patientDob")) existing.setPatientDob(Date.valueOf(params.get("patientDob")));
                    
                    // Check-in, Doctor, Nurse are preserved from 'existing' automatically.

                    // Media Handling: If no new files, try to restore existing ones
                    if (uploadedFiles.isEmpty()) {
                        String role = getRoleFromRequest(t);
                        boolean isDoctor = "doctor".equalsIgnoreCase(role);
                        try {
                            // Decrypt and restore to disk
                            patientService.decryptAndRestore(existing, isDoctor);
                            
                            // Find restored files
                            try (java.util.stream.Stream<Path> stream = Files.list(Paths.get("media"))) {
                                List<Path> restored = stream
                                    .filter(p -> p.getFileName().toString().startsWith("restored_" + existing.getRecordIndex() + "_"))
                                    .collect(java.util.stream.Collectors.toList());
                                uploadedFiles.addAll(restored);
                            }
                        } catch (Exception e) {
                            System.out.println("Warning: Could not restore media for update: " + e.getMessage());
                        }
                    }
                    
                    patientService.processEncryption(existing, params.get("symptoms"), params.get("diagnosis"), uploadedFiles);
                    repository.update(existing);
                    
                    // Cleanup
                    for (Path p : uploadedFiles) {
                        try { Files.deleteIfExists(p); } catch (Exception ignore) {}
                    }
                    
                    sendResponse(t, 200, "Updated");
                } catch (Exception e) {
                    e.printStackTrace();
                    sendResponse(t, 500, "Error: " + e.getMessage());
                }
            }
        }
    }

    // Helpers
    private static void sendResponse(HttpExchange t, int code, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        t.sendResponseHeaders(code, bytes.length);
        OutputStream os = t.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private static Map<String, String> parseJsonBody(InputStream is) throws IOException {
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> map = new HashMap<>();
        body = body.trim().replace("{", "").replace("}", "").replace("\"", "");
        String[] pairs = body.split(",");
        for (String pair : pairs) {
            String[] parts = pair.split(":");
            if (parts.length == 2) {
                map.put(parts[0].trim(), parts[1].trim());
            }
        }
        return map;
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null) return map;
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                map.put(entry[0], java.net.URLDecoder.decode(entry[1], StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    private static String toJson(Map<String, List<String>> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        int i = 0;
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            sb.append("\"").append(entry.getKey()).append("\":");
            sb.append("[");
            List<String> list = entry.getValue();
            for (int j = 0; j < list.size(); j++) {
                sb.append("\"").append(list.get(j)).append("\"");
                if (j < list.size() - 1) sb.append(",");
            }
            sb.append("]");
            if (i < map.size() - 1) sb.append(",");
            i++;
        }
        sb.append("}");
        return sb.toString();
    }

    private static String toJson(List<Map<String, Object>> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append("{");
            Map<String, Object> map = list.get(i);
            int j = 0;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                Object value = entry.getValue();
                sb.append("\"").append(entry.getKey()).append("\":");
                
                if (value instanceof List) {
                    List<?> l = (List<?>) value;
                    sb.append("[");
                    for (int k = 0; k < l.size(); k++) {
                        sb.append("\"").append(l.get(k)).append("\"");
                        if (k < l.size() - 1) sb.append(",");
                    }
                    sb.append("]");
                } else {
                    sb.append("\"").append(value).append("\"");
                }
                
                if (j < map.size() - 1) sb.append(",");
                j++;
            }
            sb.append("}");
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String getRoleFromRequest(HttpExchange t) {
        String auth = t.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            String sessionVal = sessions.get(token);
            if (sessionVal != null && sessionVal.contains(":")) {
                return sessionVal.split(":")[1];
            }
            return sessionVal;
        }
        return "unknown";
    }
}
