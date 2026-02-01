package server;

import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import model.PatientRecord;
import repository.HospitalRepository;
import repository.MySQLHospitalRepository;
import util.DatabaseConfig;
import util.DatabaseFactory;

import javax.net.ssl.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.*;

/**
 * E2EE Web Server for Hospital Record System.
 * 
 * SECURITY ARCHITECTURE:
 * - Server NEVER decrypts medical data
 * - Server NEVER stores or accesses client private keys
 * - All encryption/decryption happens on CLIENT side
 * - Server only handles: authentication, RBAC, routing, persistence
 * - RBAC is enforced using metadata fields, not decrypted content
 */
public class E2EEWebServer {

    private static final int PORT = 8000;
    private static final HospitalRepository repository = new MySQLHospitalRepository();
    
    // User sessions (in production, use proper session management)
    private static final Map<String, UserSession> sessions = new HashMap<>();

    public void start() throws Exception {
        // Load Server Keystore for mTLS
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
                    sslParams.setNeedClientAuth(true); // Enforce mTLS
                    params.setSSLParameters(sslParams);
                } catch (Exception ex) {
                    System.out.println("Failed to configure HTTPS");
                }
            }
        });

        // Static file handler
        server.createContext("/", new StaticHandler());
        
        // API Endpoints
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/logout", new LogoutHandler());
        server.createContext("/api/insert", new InsertHandler());
        server.createContext("/api/search", new SearchHandler());
        server.createContext("/api/update", new UpdateHandler());
        server.createContext("/api/keys", new PublicKeyHandler());
        server.createContext("/api/user", new UserInfoHandler());

        server.setExecutor(null);
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║           E2EE Hospital Server Started                       ║");
        System.out.println("║                                                              ║");
        System.out.println("║   URL: https://localhost:" + PORT + "                              ║");
        System.out.println("║   Mode: " + DatabaseConfig.getDatabaseType() + "                                      ║");
        System.out.println("║                                                              ║");
        System.out.println("║   SECURITY: Server NEVER decrypts medical data              ║");
        System.out.println("║             All encryption happens CLIENT-SIDE              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        server.start();
    }

    // ==================== USER SESSION ====================
    
    static class UserSession {
        String userId;
        String role;
        long createdAt;
        
        UserSession(String userId, String role) {
            this.userId = userId;
            this.role = role;
            this.createdAt = System.currentTimeMillis();
        }
    }

    // ==================== HANDLERS ====================

    static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String path = t.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            
            if (path.contains("..")) {
                sendResponse(t, 403, "Forbidden");
                return;
            }

            try {
                byte[] content = Files.readAllBytes(Paths.get("src/web" + path));
                String contentType = getContentType(path);
                t.getResponseHeaders().set("Content-Type", contentType);
                t.sendResponseHeaders(200, content.length);
                OutputStream os = t.getResponseBody();
                os.write(content);
                os.close();
            } catch (IOException e) {
                sendResponse(t, 404, "File Not Found");
            }
        }
        
        private String getContentType(String path) {
            if (path.endsWith(".html")) return "text/html";
            if (path.endsWith(".js")) return "application/javascript";
            if (path.endsWith(".css")) return "text/css";
            if (path.endsWith(".json")) return "application/json";
            return "text/plain";
        }
    }

    /**
     * Login Handler - Authenticates user and creates session.
     * Uses mTLS certificate + username/password.
     */
    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if (!"POST".equals(t.getRequestMethod())) {
                sendResponse(t, 405, "Method Not Allowed");
                return;
            }

            try {
                Map<String, String> params = parseJsonBody(t.getRequestBody());
                String user = params.get("user");
                String pass = params.get("pass");
                String dbType = params.get("dbType");
                
                // Set database type if provided
                if (dbType != null) {
                    if ("supabase".equalsIgnoreCase(dbType)) {
                        DatabaseConfig.setDatabaseType(DatabaseConfig.DatabaseType.SUPABASE_POSTGRESQL);
                    } else {
                        DatabaseConfig.setDatabaseType(DatabaseConfig.DatabaseType.MYSQL);
                        DatabaseConfig.setMySQLCredentials(user, pass);
                    }
                } else {
                    DatabaseConfig.setMySQLCredentials(user, pass);
                }
                
                // Test connection
                DatabaseFactory.getConnection().close();
                
                // Get role from certificate
                String role = getRoleFromCertificate(t);
                String userId = user;
                
                // Create session
                String sessionId = UUID.randomUUID().toString();
                sessions.put(sessionId, new UserSession(userId, role));
                
                // Return session info and role
                String json = String.format(
                    "{\"status\":\"success\",\"sessionId\":\"%s\",\"role\":\"%s\",\"userId\":\"%s\",\"dbType\":\"%s\"}",
                    sessionId, role, userId, DatabaseConfig.getDatabaseType()
                );
                
                t.getResponseHeaders().set("Content-Type", "application/json");
                sendResponse(t, 200, json);
                
            } catch (Exception e) {
                sendJsonError(t, 401, "Login Failed: " + e.getMessage());
            }
        }
    }

    static class LogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String sessionId = getSessionId(t);
            if (sessionId != null) {
                sessions.remove(sessionId);
            }
            sendResponse(t, 200, "{\"status\":\"logged out\"}");
        }
    }

    /**
     * User Info Handler - Returns current user info and available public keys.
     */
    static class UserInfoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            UserSession session = getSession(t);
            if (session == null) {
                sendJsonError(t, 401, "Not authenticated");
                return;
            }

            String json = String.format(
                "{\"userId\":\"%s\",\"role\":\"%s\"}",
                session.userId, session.role
            );
            t.getResponseHeaders().set("Content-Type", "application/json");
            sendResponse(t, 200, json);
        }
    }

    /**
     * Public Key Handler - Returns public RSA keys for encryption.
     * Clients use these to encrypt AES keys for recipients.
     */
    static class PublicKeyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                String doctorKey = new String(Files.readAllBytes(Paths.get("keys/doctor/public.key")));
                String nurseKey = new String(Files.readAllBytes(Paths.get("keys/nurse/public.key")));
                
                String json = String.format(
                    "{\"doctor\":\"%s\",\"nurse\":\"%s\"}",
                    escapeJson(doctorKey), escapeJson(nurseKey)
                );
                t.getResponseHeaders().set("Content-Type", "application/json");
                sendResponse(t, 200, json);
            } catch (Exception e) {
                sendJsonError(t, 500, "Failed to load public keys");
            }
        }
    }

    /**
     * Insert Handler - Stores pre-encrypted data from client.
     * 
     * SECURITY: Server receives already-encrypted data.
     * Server does NOT encrypt or decrypt anything.
     */
    static class InsertHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if (!"POST".equals(t.getRequestMethod())) {
                sendResponse(t, 405, "Method Not Allowed");
                return;
            }

            UserSession session = getSession(t);
            if (session == null) {
                sendJsonError(t, 401, "Not authenticated");
                return;
            }
            
            // RBAC: Only doctors can create records
            if (!"doctor".equalsIgnoreCase(session.role)) {
                sendJsonError(t, 403, "Only doctors can create records");
                return;
            }

            try {
                Map<String, String> params = parseJsonBody(t.getRequestBody());
                
                PatientRecord record = new PatientRecord();
                record.setPatientId(params.get("patientId"));
                record.setPatientName(params.get("patientName"));
                record.setPatientDob(Date.valueOf(params.get("patientDob")));
                record.setDoctorName(params.get("doctorName"));
                record.setNurseName(params.get("nurseName"));
                
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
                
                // RBAC Metadata
                record.setCreatedBy(session.userId);
                record.setCreatedByRole(session.role);
                record.setAllowedRoles(params.getOrDefault("allowedRoles", "doctor,nurse"));
                record.setRecipientIds(params.get("recipientIds"));
                
                // Encrypted data (already encrypted by client - stored as-is)
                record.setEncryptedSymptoms(params.get("encryptedSymptoms"));
                record.setEncryptedDiagnosis(params.get("encryptedDiagnosis"));
                record.setEncryptedImages(params.get("encryptedImages"));
                record.setEncryptedVideos(params.get("encryptedVideos"));
                record.setDoctorEncryptedAesKey(params.get("doctorEncryptedAesKey"));
                record.setNurseEncryptedAesKey(params.get("nurseEncryptedAesKey"));
                
                repository.insert(record);
                
                t.getResponseHeaders().set("Content-Type", "application/json");
                sendResponse(t, 200, "{\"status\":\"success\",\"message\":\"Record inserted\"}");
                
            } catch (Exception e) {
                e.printStackTrace();
                sendJsonError(t, 500, "Insert failed: " + e.getMessage());
            }
        }
    }

    /**
     * Search Handler - Returns encrypted records to client.
     * 
     * SECURITY: Server returns encrypted data as-is.
     * Client must decrypt using their private key.
     * RBAC filtering happens based on metadata.
     */
    static class SearchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if (!"GET".equals(t.getRequestMethod())) {
                sendResponse(t, 405, "Method Not Allowed");
                return;
            }

            UserSession session = getSession(t);
            if (session == null) {
                sendJsonError(t, 401, "Not authenticated");
                return;
            }

            try {
                Map<String, String> queryParams = parseQueryParams(t.getRequestURI().getQuery());
                String type = queryParams.get("type");
                String query = queryParams.get("query");
                
                // Search with RBAC filtering
                List<PatientRecord> results = repository.searchWithRBAC(
                    query, type, session.role, session.userId
                );
                
                // Return encrypted records (client will decrypt)
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < results.size(); i++) {
                    PatientRecord r = results.get(i);
                    if (i > 0) json.append(",");
                    json.append("{");
                    json.append("\"recordIndex\":").append(r.getRecordIndex()).append(",");
                    json.append("\"patientName\":\"").append(escapeJson(r.getPatientName())).append("\",");
                    json.append("\"patientDob\":\"").append(r.getPatientDob()).append("\",");
                    json.append("\"checkInDate\":\"").append(r.getCheckInDate() != null ? r.getCheckInDate() : "").append("\",");
                    json.append("\"doctorName\":\"").append(escapeJson(r.getDoctorName())).append("\",");
                    json.append("\"nurseName\":\"").append(escapeJson(r.getNurseName())).append("\",");
                    json.append("\"createdBy\":\"").append(escapeJson(r.getCreatedBy() != null ? r.getCreatedBy() : "")).append("\",");
                    json.append("\"createdByRole\":\"").append(escapeJson(r.getCreatedByRole() != null ? r.getCreatedByRole() : "")).append("\",");
                    json.append("\"allowedRoles\":\"").append(escapeJson(r.getAllowedRoles() != null ? r.getAllowedRoles() : "")).append("\",");
                    // Encrypted fields (client must decrypt)
                    json.append("\"encryptedSymptoms\":\"").append(escapeJson(r.getEncryptedSymptoms() != null ? r.getEncryptedSymptoms() : "")).append("\",");
                    json.append("\"encryptedDiagnosis\":\"").append(escapeJson(r.getEncryptedDiagnosis() != null ? r.getEncryptedDiagnosis() : "")).append("\",");
                    json.append("\"encryptedImages\":\"").append(escapeJson(r.getEncryptedImages() != null ? r.getEncryptedImages() : "")).append("\",");
                    json.append("\"encryptedVideos\":\"").append(escapeJson(r.getEncryptedVideos() != null ? r.getEncryptedVideos() : "")).append("\",");
                    json.append("\"doctorEncryptedAesKey\":\"").append(escapeJson(r.getDoctorEncryptedAesKey() != null ? r.getDoctorEncryptedAesKey() : "")).append("\",");
                    json.append("\"nurseEncryptedAesKey\":\"").append(escapeJson(r.getNurseEncryptedAesKey() != null ? r.getNurseEncryptedAesKey() : "")).append("\"");
                    json.append("}");
                }
                json.append("]");
                
                t.getResponseHeaders().set("Content-Type", "application/json");
                sendResponse(t, 200, json.toString());
                
            } catch (Exception e) {
                e.printStackTrace();
                sendJsonError(t, 500, "Search failed: " + e.getMessage());
            }
        }
    }

    /**
     * Update Handler - Updates encrypted record.
     * 
     * SECURITY: Receives pre-encrypted data from client.
     * Server does NOT decrypt or re-encrypt.
     */
    static class UpdateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if (!"POST".equals(t.getRequestMethod())) {
                sendResponse(t, 405, "Method Not Allowed");
                return;
            }

            UserSession session = getSession(t);
            if (session == null) {
                sendJsonError(t, 401, "Not authenticated");
                return;
            }
            
            // RBAC: Only doctors can update records
            if (!"doctor".equalsIgnoreCase(session.role)) {
                sendJsonError(t, 403, "Only doctors can update records");
                return;
            }

            try {
                Map<String, String> params = parseJsonBody(t.getRequestBody());
                
                PatientRecord record = new PatientRecord();
                record.setRecordIndex(Integer.parseInt(params.get("recordIndex")));
                record.setPatientName(params.get("patientName"));
                record.setPatientDob(Date.valueOf(params.get("patientDob")));
                record.setDoctorName(params.get("doctorName"));
                record.setNurseName(params.get("nurseName"));
                
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
                
                // RBAC Metadata
                record.setAllowedRoles(params.getOrDefault("allowedRoles", "doctor,nurse"));
                record.setRecipientIds(params.get("recipientIds"));
                
                // Encrypted data (already encrypted by client)
                record.setEncryptedSymptoms(params.get("encryptedSymptoms"));
                record.setEncryptedDiagnosis(params.get("encryptedDiagnosis"));
                record.setEncryptedImages(params.get("encryptedImages"));
                record.setEncryptedVideos(params.get("encryptedVideos"));
                record.setDoctorEncryptedAesKey(params.get("doctorEncryptedAesKey"));
                record.setNurseEncryptedAesKey(params.get("nurseEncryptedAesKey"));
                
                repository.update(record);
                
                t.getResponseHeaders().set("Content-Type", "application/json");
                sendResponse(t, 200, "{\"status\":\"success\",\"message\":\"Record updated\"}");
                
            } catch (Exception e) {
                e.printStackTrace();
                sendJsonError(t, 500, "Update failed: " + e.getMessage());
            }
        }
    }

    // ==================== HELPERS ====================

    private static void sendResponse(HttpExchange t, int code, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        t.sendResponseHeaders(code, bytes.length);
        OutputStream os = t.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private static void sendJsonError(HttpExchange t, int code, String message) throws IOException {
        String json = String.format("{\"status\":\"error\",\"message\":\"%s\"}", escapeJson(message));
        t.getResponseHeaders().set("Content-Type", "application/json");
        sendResponse(t, code, json);
    }

    private static Map<String, String> parseJsonBody(InputStream is) throws IOException {
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        return parseJsonObject(body);
    }

    private static Map<String, String> parseJsonObject(String json) {
        Map<String, String> map = new HashMap<>();
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);
        
        // Simple JSON parser (handles escaped quotes)
        StringBuilder key = new StringBuilder();
        StringBuilder value = new StringBuilder();
        boolean inKey = false;
        boolean inValue = false;
        boolean escaped = false;
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
            if (escaped) {
                if (inKey) key.append(c);
                else if (inValue) value.append(c);
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                if (!inKey && !inValue) {
                    inKey = true;
                } else if (inKey) {
                    inKey = false;
                } else if (inValue) {
                    inValue = false;
                    map.put(key.toString(), value.toString());
                    key = new StringBuilder();
                    value = new StringBuilder();
                }
                continue;
            }
            
            if (c == ':' && !inKey && !inValue) {
                inValue = false;
                continue;
            }
            
            if (c == ',' && !inKey && !inValue) {
                continue;
            }
            
            if (c == '"' && !inValue && key.length() > 0) {
                inValue = true;
                continue;
            }
            
            if (inKey) {
                key.append(c);
            } else if (!inKey && !inValue && key.length() > 0 && c != ' ' && c != ':') {
                inValue = true;
                value.append(c);
            } else if (inValue) {
                value.append(c);
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

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String getRoleFromCertificate(HttpExchange t) {
        try {
            if (t instanceof HttpsExchange) {
                SSLSession session = ((HttpsExchange) t).getSSLSession();
                if (session != null) {
                    java.security.cert.Certificate[] certs = session.getPeerCertificates();
                    if (certs.length > 0 && certs[0] instanceof X509Certificate) {
                        X509Certificate x509 = (X509Certificate) certs[0];
                        String dn = x509.getSubjectX500Principal().getName();
                        String cn = "";
                        for (String part : dn.split(",")) {
                            if (part.trim().startsWith("CN=")) {
                                cn = part.trim().substring(3);
                                break;
                            }
                        }
                        if (cn.toLowerCase().contains("doctor")) return "doctor";
                        if (cn.toLowerCase().contains("nurse")) return "nurse";
                        if (cn.toLowerCase().contains("admin")) return "admin";
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "unknown";
    }

    private static String getSessionId(HttpExchange t) {
        String auth = t.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }

    private static UserSession getSession(HttpExchange t) {
        String sessionId = getSessionId(t);
        if (sessionId != null) {
            return sessions.get(sessionId);
        }
        // Fallback: create session from certificate
        String role = getRoleFromCertificate(t);
        if (!"unknown".equals(role)) {
            return new UserSession("cert-user", role);
        }
        return null;
    }
}
