import com.sun.net.httpserver.*;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.util.*;

public class Server {
    static Connection db;
    static Properties cfg = new Properties();
    static HttpClient httpClient = HttpClient.newHttpClient();
    static String TABLE, SCHEMA;
    static boolean isSupabase, mtlsEnabled;

    public static void main(String[] args) throws Exception {
        cfg.load(new FileInputStream("config.properties"));
        TABLE = cfg.getProperty("db.table", "patient_records");
        SCHEMA = cfg.getProperty("db.schema", "public");
        isSupabase = "supabase".equals(cfg.getProperty("db.type"));
        mtlsEnabled = "true".equals(cfg.getProperty("mtls.enabled", "false"));
        
        if (!isSupabase) connectDB();
        
        int port = Integer.parseInt(cfg.getProperty("port", "8000"));
        HttpsServer srv = setupSSL(port);
        
        srv.createContext("/api/records", Server::handleRecords);
        srv.createContext("/api/login", Server::handleLogin);
        srv.createContext("/", Server::serveStatic);
        srv.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
        srv.start();
        
        System.out.println("Server running on https://localhost:" + port);
        System.out.println("DB: " + (isSupabase ? "Supabase" : "MySQL"));
        System.out.println("mTLS: " + (mtlsEnabled ? "ENABLED (client certs required)" : "DISABLED"));
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
        
        // Server keystore (server identity)
        KeyStore ks = KeyStore.getInstance("PKCS12");
        String ksPath = cfg.getProperty("mtls.keystore", "certs/server.p12");
        String ksPwd = cfg.getProperty("mtls.password", "password");
        ks.load(new FileInputStream(ksPath), ksPwd.toCharArray());
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, ksPwd.toCharArray());
        
        TrustManager[] tms = null;
        if (mtlsEnabled) {
            // Truststore (trusted client certs)
            KeyStore ts = KeyStore.getInstance("PKCS12");
            String tsPath = cfg.getProperty("mtls.truststore", "certs/truststore.p12");
            ts.load(new FileInputStream(tsPath), ksPwd.toCharArray());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(ts);
            tms = tmf.getTrustManagers();
        }
        
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(kmf.getKeyManagers(), tms, null);
        
        srv.setHttpsConfigurator(new HttpsConfigurator(ssl) {
            public void configure(HttpsParameters params) {
                SSLContext c = getSSLContext();
                SSLParameters sslp = c.getDefaultSSLParameters();
                if (mtlsEnabled) {
                    sslp.setNeedClientAuth(true); // Require client cert
                }
                params.setSSLParameters(sslp);
            }
        });
        return srv;
    }

    static void handleRecords(HttpExchange ex) throws IOException {
        cors(ex);
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        try {
            if ("GET".equals(method)) {
                String q = ex.getRequestURI().getQuery();
                Map<String,String> params = parseQuery(q);
                String json = isSupabase ? supabaseGet(params) : mysqlGet(params);
                send(ex, 200, json);
            } else if ("POST".equals(method)) {
                String body = new String(ex.getRequestBody().readAllBytes());
                if (isSupabase) supabasePost(body); else mysqlPost(body);
                send(ex, 201, "{\"ok\":true}");
            } else if ("PUT".equals(method)) {
                // UPDATE - Doctor only (checked on client, but also here)
                String hid = path.substring(path.lastIndexOf('/') + 1);
                String body = new String(ex.getRequestBody().readAllBytes());
                if (isSupabase) supabasePut(hid, body); else mysqlPut(hid, body);
                send(ex, 200, "{\"ok\":true}");
            } else if ("OPTIONS".equals(method)) {
                ex.sendResponseHeaders(204, -1);
            }
        } catch (Exception e) { e.printStackTrace(); send(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}"); }
    }
    
    static Map<String,String> parseQuery(String q) {
        Map<String,String> m = new HashMap<>();
        if (q != null) for (String p : q.split("&")) {
            String[] kv = p.split("=");
            if (kv.length == 2) m.put(kv[0], kv[1]);
        }
        return m;
    }

    static String mysqlGet(Map<String,String> params) throws Exception {
        StringBuilder sql = new StringBuilder("SELECT * FROM " + TABLE + " WHERE 1=1");
        List<String> vals = new ArrayList<>();
        
        if (params.containsKey("id")) { sql.append(" AND hashed_patient_id=?"); vals.add(params.get("id")); }
        if (params.containsKey("role")) { sql.append(" AND allowed_roles LIKE ?"); vals.add("%" + params.get("role") + "%"); }
        if (params.containsKey("createdBy")) { sql.append(" AND created_by_role=?"); vals.add(params.get("createdBy")); }
        
        PreparedStatement ps = db.prepareStatement(sql.toString());
        for (int i = 0; i < vals.size(); i++) ps.setString(i + 1, vals.get(i));
        ResultSet rs = ps.executeQuery();
        StringBuilder json = new StringBuilder("[");
        while (rs.next()) {
            if (json.length() > 1) json.append(",");
            json.append("{\"hashedPatientId\":\"").append(rs.getString("hashed_patient_id")).append("\"");
            json.append(",\"encryptedName\":\"").append(esc(rs.getString("encrypted_name"))).append("\"");
            json.append(",\"encryptedDiagnosis\":\"").append(esc(rs.getString("encrypted_diagnosis"))).append("\"");
            json.append(",\"encryptedTreatment\":\"").append(esc(rs.getString("encrypted_treatment"))).append("\"");
            json.append(",\"encryptedPrescription\":\"").append(esc(rs.getString("encrypted_prescription"))).append("\"");
            json.append(",\"encryptedMedia\":\"").append(esc(rs.getString("encrypted_media"))).append("\"");
            json.append(",\"mediaType\":\"").append(esc(rs.getString("media_type"))).append("\"");
            json.append(",\"createdByRole\":\"").append(rs.getString("created_by_role")).append("\"");
            json.append(",\"allowedRoles\":\"").append(rs.getString("allowed_roles")).append("\"}");
        }
        return json.append("]").toString();
    }

    static void mysqlPost(String body) throws Exception {
        Map<String,String> d = parseJson(body);
        PreparedStatement ps = db.prepareStatement("INSERT INTO " + TABLE + " (hashed_patient_id,encrypted_name,encrypted_diagnosis,encrypted_treatment,encrypted_prescription,encrypted_media,media_type,created_by_role,allowed_roles) VALUES (?,?,?,?,?,?,?,?,?)");
        ps.setString(1, d.get("hashedPatientId"));
        ps.setString(2, d.get("encryptedName"));
        ps.setString(3, d.get("encryptedDiagnosis"));
        ps.setString(4, d.get("encryptedTreatment"));
        ps.setString(5, d.get("encryptedPrescription"));
        ps.setString(6, d.get("encryptedMedia"));
        ps.setString(7, d.get("mediaType"));
        ps.setString(8, d.getOrDefault("createdByRole", "unknown"));
        ps.setString(9, d.getOrDefault("allowedRoles", "doctor,nurse"));
        ps.executeUpdate();
    }
    
    static void mysqlPut(String hid, String body) throws Exception {
        Map<String,String> d = parseJson(body);
        PreparedStatement ps = db.prepareStatement("UPDATE " + TABLE + " SET encrypted_name=?,encrypted_diagnosis=?,encrypted_treatment=?,encrypted_prescription=?,encrypted_media=?,media_type=? WHERE hashed_patient_id=?");
        ps.setString(1, d.get("encryptedName"));
        ps.setString(2, d.get("encryptedDiagnosis"));
        ps.setString(3, d.get("encryptedTreatment"));
        ps.setString(4, d.get("encryptedPrescription"));
        ps.setString(5, d.get("encryptedMedia"));
        ps.setString(6, d.get("mediaType"));
        ps.setString(7, hid);
        ps.executeUpdate();
    }

    static String supabaseGet(Map<String,String> params) throws Exception {
        StringBuilder url = new StringBuilder(cfg.getProperty("supabase.url") + "/rest/v1/" + TABLE + "?select=*");
        if (params.containsKey("id")) url.append("&hashed_patient_id=eq.").append(params.get("id"));
        if (params.containsKey("role")) url.append("&allowed_roles=like.*").append(params.get("role")).append("*");
        if (params.containsKey("createdBy")) url.append("&created_by_role=eq.").append(params.get("createdBy"));
        
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url.toString()))
            .header("apikey", cfg.getProperty("supabase.key"))
            .header("Authorization", "Bearer " + cfg.getProperty("supabase.key")).GET().build();
        HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("Supabase GET: " + response.statusCode() + " " + url);
        String resp = response.body();
        return resp.replace("hashed_patient_id", "hashedPatientId")
                   .replace("encrypted_name", "encryptedName")
                   .replace("encrypted_diagnosis", "encryptedDiagnosis")
                   .replace("encrypted_treatment", "encryptedTreatment")
                   .replace("encrypted_prescription", "encryptedPrescription")
                   .replace("encrypted_media", "encryptedMedia")
                   .replace("media_type", "mediaType")
                   .replace("created_by_role", "createdByRole")
                   .replace("allowed_roles", "allowedRoles");
    }

    static void supabasePost(String body) throws Exception {
        Map<String,String> d = parseJson(body);
        StringBuilder json = new StringBuilder("{");
        json.append("\"hashed_patient_id\":\"").append(d.get("hashedPatientId")).append("\"");
        json.append(",\"encrypted_name\":\"").append(esc(d.get("encryptedName"))).append("\"");
        json.append(",\"encrypted_diagnosis\":\"").append(esc(d.get("encryptedDiagnosis"))).append("\"");
        json.append(",\"encrypted_treatment\":\"").append(esc(d.get("encryptedTreatment"))).append("\"");
        json.append(",\"encrypted_prescription\":\"").append(esc(d.get("encryptedPrescription"))).append("\"");
        if (d.get("encryptedMedia") != null) json.append(",\"encrypted_media\":\"").append(esc(d.get("encryptedMedia"))).append("\"");
        if (d.get("mediaType") != null) json.append(",\"media_type\":\"").append(d.get("mediaType")).append("\"");
        json.append(",\"created_by_role\":\"").append(d.getOrDefault("createdByRole","unknown")).append("\"");
        json.append(",\"allowed_roles\":\"").append(d.getOrDefault("allowedRoles","doctor,nurse")).append("\"}");
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(cfg.getProperty("supabase.url") + "/rest/v1/" + TABLE))
            .header("apikey", cfg.getProperty("supabase.key"))
            .header("Authorization", "Bearer " + cfg.getProperty("supabase.key"))
            .header("Content-Type", "application/json")
            .header("Prefer", "return=minimal").POST(HttpRequest.BodyPublishers.ofString(json.toString())).build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("Supabase POST: " + resp.statusCode());
        if (resp.statusCode() >= 400) throw new Exception("Supabase error: " + resp.body());
    }
    
    static void supabasePut(String hid, String body) throws Exception {
        Map<String,String> d = parseJson(body);
        StringBuilder json = new StringBuilder("{");
        json.append("\"encrypted_name\":\"").append(esc(d.get("encryptedName"))).append("\"");
        json.append(",\"encrypted_diagnosis\":\"").append(esc(d.get("encryptedDiagnosis"))).append("\"");
        json.append(",\"encrypted_treatment\":\"").append(esc(d.get("encryptedTreatment"))).append("\"");
        json.append(",\"encrypted_prescription\":\"").append(esc(d.get("encryptedPrescription"))).append("\"");
        if (d.get("encryptedMedia") != null) json.append(",\"encrypted_media\":\"").append(esc(d.get("encryptedMedia"))).append("\"");
        if (d.get("mediaType") != null) json.append(",\"media_type\":\"").append(d.get("mediaType")).append("\"");
        json.append("}");
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(cfg.getProperty("supabase.url") + "/rest/v1/" + TABLE + "?hashed_patient_id=eq." + hid))
            .header("apikey", cfg.getProperty("supabase.key"))
            .header("Authorization", "Bearer " + cfg.getProperty("supabase.key"))
            .header("Content-Type", "application/json")
            .header("Prefer", "return=minimal")
            .method("PATCH", HttpRequest.BodyPublishers.ofString(json.toString())).build();
        httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    static void handleLogin(HttpExchange ex) throws IOException {
        cors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }
        try {
            Map<String,String> d = parseJson(new String(ex.getRequestBody().readAllBytes()));
            String role = null;
            if (isSupabase) {
                String url = cfg.getProperty("supabase.url") + "/rest/v1/users?username=eq." + d.get("username") + "&password_hash=eq." + d.get("passwordHash") + "&select=role";
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                    .header("apikey", cfg.getProperty("supabase.key"))
                    .header("Authorization", "Bearer " + cfg.getProperty("supabase.key")).GET().build();
                String resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()).body();
                System.out.println("Login response: " + resp);
                if (resp.contains("role")) {
                    int i = resp.indexOf("role") + 7;
                    int j = resp.indexOf('"', i);
                    role = resp.substring(i, j);
                }
            } else {
                PreparedStatement ps = db.prepareStatement("SELECT role FROM users WHERE username=? AND password_hash=?");
                ps.setString(1, d.get("username"));
                ps.setString(2, d.get("passwordHash"));
                ResultSet rs = ps.executeQuery();
                if (rs.next()) role = rs.getString("role");
            }
            if (role != null) send(ex, 200, "{\"ok\":true,\"role\":\"" + role + "\"}");
            else send(ex, 401, "{\"error\":\"Invalid\"}");
        } catch (Exception e) { e.printStackTrace(); send(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}"); }
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

    static void cors(HttpExchange ex) { ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*"); ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,PUT,OPTIONS"); ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type"); }
    static void send(HttpExchange ex, int code, String body) throws IOException { byte[] b = body.getBytes(); ex.getResponseHeaders().set("Content-Type", "application/json"); ex.sendResponseHeaders(code, b.length); ex.getResponseBody().write(b); ex.close(); }
    static String esc(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\""); }
    static Map<String,String> parseJson(String j) { Map<String,String> m = new HashMap<>(); for (String p : j.replaceAll("[{}\"]", "").split(",")) { String[] kv = p.split(":"); if (kv.length == 2) m.put(kv[0].trim(), kv[1].trim()); } return m; }
}
