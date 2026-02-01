import com.sun.net.httpserver.*;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.util.*;

public class Server {
    static Connection db;
    static Properties cfg = new Properties();

    public static void main(String[] args) throws Exception {
        cfg.load(new FileInputStream("config.properties"));
        connectDB();
        
        int port = Integer.parseInt(cfg.getProperty("port", "8000"));
        HttpsServer srv = setupSSL(port);
        
        srv.createContext("/api/records", Server::handleRecords);
        srv.createContext("/api/login", Server::handleLogin);
        srv.createContext("/", Server::serveStatic);
        srv.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
        srv.start();
        
        System.out.println("Server running on https://localhost:" + port);
    }

    static void connectDB() throws Exception {
        String t = cfg.getProperty("db.type", "mysql");
        String url = t.equals("postgresql") 
            ? "jdbc:postgresql://" + cfg.getProperty("db.host") + ":" + cfg.getProperty("db.port") + "/" + cfg.getProperty("db.name")
            : "jdbc:mysql://" + cfg.getProperty("db.host") + ":" + cfg.getProperty("db.port") + "/" + cfg.getProperty("db.name") + "?useSSL=false&allowPublicKeyRetrieval=true";
        Class.forName(t.equals("postgresql") ? "org.postgresql.Driver" : "com.mysql.cj.jdbc.Driver");
        db = DriverManager.getConnection(url, cfg.getProperty("db.user"), cfg.getProperty("db.pass"));
    }

    static HttpsServer setupSSL(int port) throws Exception {
        HttpsServer srv = HttpsServer.create(new InetSocketAddress(port), 0);
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(new FileInputStream("certs/server.p12"), "password".toCharArray());
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, "password".toCharArray());
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(kmf.getKeyManagers(), null, null);
        srv.setHttpsConfigurator(new HttpsConfigurator(ssl));
        return srv;
    }

    static void handleRecords(HttpExchange ex) throws IOException {
        cors(ex);
        try {
            if ("GET".equals(ex.getRequestMethod())) {
                String q = ex.getRequestURI().getQuery();
                String sql = "SELECT * FROM patient_records" + (q != null && q.contains("id=") ? " WHERE hashed_patient_id=?" : "");
                PreparedStatement ps = db.prepareStatement(sql);
                if (q != null && q.contains("id=")) ps.setString(1, q.split("id=")[1].split("&")[0]);
                ResultSet rs = ps.executeQuery();
                StringBuilder json = new StringBuilder("[");
                while (rs.next()) {
                    if (json.length() > 1) json.append(",");
                    json.append("{\"hashedPatientId\":\"").append(rs.getString("hashed_patient_id")).append("\"");
                    json.append(",\"encryptedName\":\"").append(esc(rs.getString("encrypted_name"))).append("\"");
                    json.append(",\"encryptedDiagnosis\":\"").append(esc(rs.getString("encrypted_diagnosis"))).append("\"");
                    json.append(",\"encryptedTreatment\":\"").append(esc(rs.getString("encrypted_treatment"))).append("\"");
                    json.append(",\"encryptedPrescription\":\"").append(esc(rs.getString("encrypted_prescription"))).append("\"");
                    json.append(",\"createdByRole\":\"").append(rs.getString("created_by_role")).append("\"");
                    json.append(",\"allowedRoles\":\"").append(rs.getString("allowed_roles")).append("\"}");
                }
                json.append("]");
                send(ex, 200, json.toString());
            } else if ("POST".equals(ex.getRequestMethod())) {
                String body = new String(ex.getRequestBody().readAllBytes());
                Map<String,String> d = parseJson(body);
                PreparedStatement ps = db.prepareStatement("INSERT INTO patient_records (hashed_patient_id,encrypted_name,encrypted_diagnosis,encrypted_treatment,encrypted_prescription,created_by_role,allowed_roles) VALUES (?,?,?,?,?,?,?)");
                ps.setString(1, d.get("hashedPatientId"));
                ps.setString(2, d.get("encryptedName"));
                ps.setString(3, d.get("encryptedDiagnosis"));
                ps.setString(4, d.get("encryptedTreatment"));
                ps.setString(5, d.get("encryptedPrescription"));
                ps.setString(6, d.getOrDefault("createdByRole", "unknown"));
                ps.setString(7, d.getOrDefault("allowedRoles", "doctor,nurse"));
                ps.executeUpdate();
                send(ex, 201, "{\"ok\":true}");
            } else if ("OPTIONS".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(204, -1);
            }
        } catch (Exception e) { send(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}"); }
    }

    static void handleLogin(HttpExchange ex) throws IOException {
        cors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }
        try {
            Map<String,String> d = parseJson(new String(ex.getRequestBody().readAllBytes()));
            PreparedStatement ps = db.prepareStatement("SELECT role FROM users WHERE username=? AND password_hash=?");
            ps.setString(1, d.get("username"));
            ps.setString(2, d.get("passwordHash"));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) send(ex, 200, "{\"ok\":true,\"role\":\"" + rs.getString("role") + "\"}");
            else send(ex, 401, "{\"error\":\"Invalid\"}");
        } catch (Exception e) { send(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}"); }
    }

    static void serveStatic(HttpExchange ex) throws IOException {
        String p = ex.getRequestURI().getPath();
        if (p.equals("/")) p = "/index.html";
        Path f = Paths.get("web" + p);
        if (Files.exists(f)) {
            byte[] b = Files.readAllBytes(f);
            ex.getResponseHeaders().set("Content-Type", p.endsWith(".js") ? "application/javascript" : p.endsWith(".css") ? "text/css" : "text/html");
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
        } else {
            ex.sendResponseHeaders(404, 0);
        }
        ex.close();
    }

    static void cors(HttpExchange ex) { ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*"); ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,OPTIONS"); ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type"); }
    static void send(HttpExchange ex, int code, String body) throws IOException { byte[] b = body.getBytes(); ex.getResponseHeaders().set("Content-Type", "application/json"); ex.sendResponseHeaders(code, b.length); ex.getResponseBody().write(b); ex.close(); }
    static String esc(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\""); }
    static Map<String,String> parseJson(String j) { Map<String,String> m = new HashMap<>(); for (String p : j.replaceAll("[{}\"]", "").split(",")) { String[] kv = p.split(":"); if (kv.length == 2) m.put(kv[0].trim(), kv[1].trim()); } return m; }
}
