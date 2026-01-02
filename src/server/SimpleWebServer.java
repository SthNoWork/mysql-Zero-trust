package server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import model.PatientRecord;
import repository.HospitalRepository;
import repository.MySQLHospitalRepository;
import service.PatientService;
import util.DBConnection;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

public class SimpleWebServer {

    private static final int PORT = 8000;
    private static final HospitalRepository repository = new MySQLHospitalRepository();
    private static final PatientService patientService = new PatientService();

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Serve HTML
        server.createContext("/", new StaticHandler());

        // API Endpoints
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/insert", new InsertHandler());
        server.createContext("/api/search", new SearchHandler());
        server.createContext("/api/update", new UpdateHandler());

        server.setExecutor(null); // creates a default executor
        System.out.println("Server started on http://localhost:" + PORT);
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
                Map<String, String> params = parseJsonBody(t.getRequestBody());
                try {
                    DBConnection.setCredentials(params.get("user"), params.get("pass"));
                    // Test connection
                    DBConnection.getConnection().close();
                    sendResponse(t, 200, "Login Successful");
                } catch (Exception e) {
                    sendResponse(t, 401, "Login Failed: " + e.getMessage());
                }
            } else {
                sendResponse(t, 405, "Method Not Allowed");
            }
        }
    }

    static class InsertHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                try {
                    String contentType = t.getRequestHeaders().getFirst("Content-Type");
                    Map<String, String> params = new HashMap<>();

                    if (contentType != null && contentType.contains("multipart/form-data")) {
                        // Handle Multipart
                        String boundary = contentType.substring(contentType.indexOf("boundary=") + 9);
                        parseMultipart(t.getRequestBody(), boundary, params);
                    } else {
                        // Handle JSON
                        params = parseJsonBody(t.getRequestBody());
                    }
                    
                    PatientRecord record = new PatientRecord();
                    record.setPatientId(params.get("patientId"));
                    record.setPatientName(params.get("patientName"));
                    record.setPatientDob(Date.valueOf(params.get("patientDob")));
                    record.setDoctorName(params.get("doctorName"));
                    record.setNurseName(params.get("nurseName"));

                    patientService.processEncryption(record, params.get("symptoms"), params.get("diagnosis"));
                    repository.insert(record);
                    
                    sendResponse(t, 200, "Inserted");
                } catch (Exception e) {
                    e.printStackTrace();
                    sendResponse(t, 500, "Error: " + e.getMessage());
                }
            }
        }

        private void parseMultipart(InputStream is, String boundary, Map<String, String> params) throws IOException {
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
                            Files.write(Paths.get("media", filename), fileBytes);
                            System.out.println("Saved uploaded file: " + filename);
                        }
                    } else {
                        // It's a field
                        params.put(name, content);
                    }
                }
            }
        }

        private String extractHeaderValue(String headers, String key) {
            for (String line : headers.split("\r\n")) {
                if (line.contains(key + "=")) {
                    int start = line.indexOf(key + "=\"") + key.length() + 2;
                    int end = line.indexOf("\"", start);
                    return line.substring(start, end);
                }
            }
            return null;
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
                    String role = queryParams.get("role");
                    boolean isDoctor = "doctor".equalsIgnoreCase(role);

                    List<PatientRecord> results = repository.search(query, type);
                    
                    // Decrypt results for display
                    List<Map<String, Object>> jsonResults = new ArrayList<>();
                    for (PatientRecord r : results) {
                        try {
                            String[] decrypted = patientService.decryptMedicalData(r, isDoctor);
                            Map<String, String> media = patientService.getDecryptedMedia(r, isDoctor);
                            
                            Map<String, Object> map = new HashMap<>();
                            map.put("patientName", r.getPatientName());
                            map.put("patientDob", r.getPatientDob().toString());
                            map.put("doctorName", r.getDoctorName());
                            map.put("nurseName", r.getNurseName());
                            map.put("symptoms", decrypted[0]);
                            map.put("diagnosis", decrypted[1]);
                            map.put("recordIndex", r.getRecordIndex());
                            
                            if (media.containsKey("image")) map.put("image", media.get("image"));
                            if (media.containsKey("video")) map.put("video", media.get("video"));
                            
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

    static class UpdateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                try {
                    Map<String, String> params = parseJsonBody(t.getRequestBody());
                    
                    PatientRecord record = new PatientRecord();
                    record.setRecordIndex(Integer.parseInt(params.get("recordIndex")));
                    
                    record.setPatientName(params.get("patientName"));
                    record.setPatientDob(Date.valueOf(params.get("patientDob")));
                    record.setDoctorName(params.get("doctorName"));
                    record.setNurseName(params.get("nurseName"));

                    patientService.processEncryption(record, params.get("symptoms"), params.get("diagnosis"));
                    repository.update(record);
                    
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

    private static String toJson(List<Map<String, Object>> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append("{");
            Map<String, Object> map = list.get(i);
            int j = 0;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                sb.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
                if (j < map.size() - 1) sb.append(",");
                j++;
            }
            sb.append("}");
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}
