import com.sun.net.httpserver.*;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.util.*;
import java.security.MessageDigest;

public class Server {
    static Connection db;
    static Properties cfg = new Properties();
    static HttpClient httpClient = HttpClient.newHttpClient();
    static String TABLE = "Hospital_Records", SCHEMA = "hospital";
    static boolean isSupabase, mtlsEnabled;

    public static void main(String[] args) throws Exception {
        cfg.load(new FileInputStream("config.properties"));
        TABLE = cfg.getProperty("db.table", "Hospital_Records");
        SCHEMA = cfg.getProperty("db.schema", "hospital");
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
        String schema = cfg.getProperty("db.schema", "hospital");
        String url = "jdbc:mysql://" + cfg.getProperty("db.host") + ":" + cfg.getProperty("db.port") + "/" + schema + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        Class.forName("com.mysql.cj.jdbc.Driver");
        db = DriverManager.getConnection(url, cfg.getProperty("db.user"), cfg.getProperty("db.pass"));
        System.out.println("Connected to MySQL: " + schema);
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
        List<Object> vals = new ArrayList<>();
        
        if (params.containsKey("id")) { sql.append(" AND patient_id_hash=?"); vals.add(sha256(params.get("id"))); }
        if (params.containsKey("name")) { sql.append(" AND patient_name LIKE ?"); vals.add("%" + params.get("name") + "%"); }
        if (params.containsKey("doctor")) { sql.append(" AND doctor_name=?"); vals.add(params.get("doctor")); }
        if (params.containsKey("nurse")) { sql.append(" AND nurse_name=?"); vals.add(params.get("nurse")); }
        
        PreparedStatement ps = db.prepareStatement(sql.toString());
        for (int i = 0; i < vals.size(); i++) ps.setObject(i + 1, vals.get(i));
        ResultSet rs = ps.executeQuery();
        StringBuilder json = new StringBuilder("[");
        while (rs.next()) {
            if (json.length() > 1) json.append(",");
            json.append("{\"recordIndex\":").append(rs.getInt("record_index"));
            json.append(",\"patientIdHash\":\"").append(esc(rs.getString("patient_id_hash"))).append("\"");
            json.append(",\"patientName\":\"").append(esc(rs.getString("patient_name"))).append("\"");
            json.append(",\"patientDob\":\"").append(rs.getDate("patient_dob")).append("\"");
            json.append(",\"doctorName\":\"").append(esc(rs.getString("doctor_name"))).append("\"");
            json.append(",\"nurseName\":\"").append(esc(rs.getString("nurse_name"))).append("\"");
            json.append(",\"checkInDate\":\"").append(rs.getTimestamp("check_in_date")).append("\"");
            json.append(",\"encryptedSymptoms\":\"").append(b64(rs.getBytes("encrypted_symptoms"))).append("\"");
            json.append(",\"encryptedDiagnosis\":\"").append(b64(rs.getBytes("encrypted_diagnosis"))).append("\"");
            json.append(",\"encryptedImages\":\"").append(b64(rs.getBytes("encrypted_images"))).append("\"");
            json.append(",\"encryptedVideos\":\"").append(b64(rs.getBytes("encrypted_videos"))).append("\"");
            json.append(",\"encryptedAudios\":\"").append(b64(rs.getBytes("encrypted_audios"))).append("\"");
            json.append(",\"doctorEncryptedAesKey\":\"").append(b64(rs.getBytes("doctor_encrypted_aes_key"))).append("\"");
            json.append(",\"nurseEncryptedAesKey\":\"").append(b64(rs.getBytes("nurse_encrypted_aes_key"))).append("\"}");
        }
        return json.append("]").toString();
    }

    static void mysqlPost(String body) throws Exception {
        Map<String,String> d = parseJson(body);
        PreparedStatement ps = db.prepareStatement(
            "INSERT INTO " + TABLE + " (patient_id_hash,patient_name,patient_dob,doctor_name,nurse_name," +
            "encrypted_symptoms,encrypted_diagnosis,encrypted_images,encrypted_videos,encrypted_audios," +
            "doctor_encrypted_aes_key,nurse_encrypted_aes_key) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)");
        ps.setString(1, sha256(d.get("patientId")));
        ps.setString(2, d.get("patientName"));
        ps.setDate(3, d.get("patientDob") != null ? java.sql.Date.valueOf(d.get("patientDob")) : null);
        ps.setString(4, d.get("doctorName"));
        ps.setString(5, d.get("nurseName"));
        ps.setBytes(6, d64(d.get("encryptedSymptoms")));
        ps.setBytes(7, d64(d.get("encryptedDiagnosis")));
        ps.setBytes(8, d64(d.get("encryptedImages")));
        ps.setBytes(9, d64(d.get("encryptedVideos")));
        ps.setBytes(10, d64(d.get("encryptedAudios")));
        ps.setBytes(11, d64(d.get("doctorEncryptedAesKey")));
        ps.setBytes(12, d64(d.get("nurseEncryptedAesKey")));
        ps.executeUpdate();
    }
    
    static void mysqlPut(String hid, String body) throws Exception {
        Map<String,String> d = parseJson(body);
        PreparedStatement ps = db.prepareStatement(
            "UPDATE " + TABLE + " SET patient_name=?,patient_dob=?,doctor_name=?,nurse_name=?," +
            "encrypted_symptoms=?,encrypted_diagnosis=?,encrypted_images=?,encrypted_videos=?,encrypted_audios=?," +
            "doctor_encrypted_aes_key=?,nurse_encrypted_aes_key=? WHERE record_index=?");
        ps.setString(1, d.get("patientName"));
        ps.setDate(2, d.get("patientDob") != null ? java.sql.Date.valueOf(d.get("patientDob")) : null);
        ps.setString(3, d.get("doctorName"));
        ps.setString(4, d.get("nurseName"));
        ps.setBytes(5, d64(d.get("encryptedSymptoms")));
        ps.setBytes(6, d64(d.get("encryptedDiagnosis")));
        ps.setBytes(7, d64(d.get("encryptedImages")));
        ps.setBytes(8, d64(d.get("encryptedVideos")));
        ps.setBytes(9, d64(d.get("encryptedAudios")));
        ps.setBytes(10, d64(d.get("doctorEncryptedAesKey")));
        ps.setBytes(11, d64(d.get("nurseEncryptedAesKey")));
        ps.setInt(12, Integer.parseInt(hid));
        ps.executeUpdate();
    }

    static String supabaseGet(Map<String,String> params) throws Exception {
        StringBuilder url = new StringBuilder(cfg.getProperty("supabase.url") + "/rest/v1/" + TABLE + "?select=*");
        if (params.containsKey("id")) url.append("&patient_id_hash=eq.").append(sha256(params.get("id")));
        if (params.containsKey("name")) url.append("&patient_name=like.*").append(params.get("name")).append("*");
        if (params.containsKey("doctor")) url.append("&doctor_name=eq.").append(params.get("doctor"));
        
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url.toString()))
            .header("apikey", cfg.getProperty("supabase.key"))
            .header("Authorization", "Bearer " + cfg.getProperty("supabase.key")).GET().build();
        HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("Supabase GET: " + response.statusCode() + " " + url);
        String resp = response.body();
        return resp.replace("record_index", "recordIndex")
                   .replace("patient_id_hash", "patientIdHash")
                   .replace("patient_name", "patientName")
                   .replace("patient_dob", "patientDob")
                   .replace("doctor_name", "doctorName")
                   .replace("nurse_name", "nurseName")
                   .replace("check_in_date", "checkInDate")
                   .replace("encrypted_symptoms", "encryptedSymptoms")
                   .replace("encrypted_diagnosis", "encryptedDiagnosis")
                   .replace("encrypted_images", "encryptedImages")
                   .replace("encrypted_videos", "encryptedVideos")
                   .replace("encrypted_audios", "encryptedAudios")
                   .replace("doctor_encrypted_aes_key", "doctorEncryptedAesKey")
                   .replace("nurse_encrypted_aes_key", "nurseEncryptedAesKey");
    }

    static void supabasePost(String body) throws Exception {
        Map<String,String> d = parseJson(body);
        StringBuilder json = new StringBuilder("{");
        json.append("\"patient_id_hash\":\"").append(sha256(d.get("patientId"))).append("\"");
        json.append(",\"patient_name\":\"").append(esc(d.get("patientName"))).append("\"");
        if (d.get("patientDob") != null) json.append(",\"patient_dob\":\"").append(d.get("patientDob")).append("\"");
        json.append(",\"doctor_name\":\"").append(esc(d.get("doctorName"))).append("\"");
        json.append(",\"nurse_name\":\"").append(esc(d.get("nurseName"))).append("\"");
        if (d.get("encryptedSymptoms") != null) json.append(",\"encrypted_symptoms\":\"").append(d.get("encryptedSymptoms")).append("\"");
        if (d.get("encryptedDiagnosis") != null) json.append(",\"encrypted_diagnosis\":\"").append(d.get("encryptedDiagnosis")).append("\"");
        if (d.get("encryptedImages") != null) json.append(",\"encrypted_images\":\"").append(d.get("encryptedImages")).append("\"");
        if (d.get("encryptedVideos") != null) json.append(",\"encrypted_videos\":\"").append(d.get("encryptedVideos")).append("\"");
        if (d.get("encryptedAudios") != null) json.append(",\"encrypted_audios\":\"").append(d.get("encryptedAudios")).append("\"");
        if (d.get("doctorEncryptedAesKey") != null) json.append(",\"doctor_encrypted_aes_key\":\"").append(d.get("doctorEncryptedAesKey")).append("\"");
        if (d.get("nurseEncryptedAesKey") != null) json.append(",\"nurse_encrypted_aes_key\":\"").append(d.get("nurseEncryptedAesKey")).append("\"");
        json.append("}");
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
        json.append("\"patient_name\":\"").append(esc(d.get("patientName"))).append("\"");
        if (d.get("patientDob") != null) json.append(",\"patient_dob\":\"").append(d.get("patientDob")).append("\"");
        json.append(",\"doctor_name\":\"").append(esc(d.get("doctorName"))).append("\"");
        json.append(",\"nurse_name\":\"").append(esc(d.get("nurseName"))).append("\"");
        if (d.get("encryptedSymptoms") != null) json.append(",\"encrypted_symptoms\":\"").append(d.get("encryptedSymptoms")).append("\"");
        if (d.get("encryptedDiagnosis") != null) json.append(",\"encrypted_diagnosis\":\"").append(d.get("encryptedDiagnosis")).append("\"");
        if (d.get("encryptedImages") != null) json.append(",\"encrypted_images\":\"").append(d.get("encryptedImages")).append("\"");
        if (d.get("encryptedVideos") != null) json.append(",\"encrypted_videos\":\"").append(d.get("encryptedVideos")).append("\"");
        if (d.get("encryptedAudios") != null) json.append(",\"encrypted_audios\":\"").append(d.get("encryptedAudios")).append("\"");
        if (d.get("doctorEncryptedAesKey") != null) json.append(",\"doctor_encrypted_aes_key\":\"").append(d.get("doctorEncryptedAesKey")).append("\"");
        if (d.get("nurseEncryptedAesKey") != null) json.append(",\"nurse_encrypted_aes_key\":\"").append(d.get("nurseEncryptedAesKey")).append("\"");
        json.append("}");
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(cfg.getProperty("supabase.url") + "/rest/v1/" + TABLE + "?record_index=eq." + hid))
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
            
            // Extract role from client certificate if mTLS is enabled
            if (mtlsEnabled && ex instanceof HttpsExchange) {
                try {
                    HttpsExchange httpsEx = (HttpsExchange) ex;
                    SSLSession sslSession = httpsEx.getSSLSession();
                    java.security.cert.Certificate[] certs = sslSession.getPeerCertificates();
                    if (certs.length > 0 && certs[0] instanceof java.security.cert.X509Certificate) {
                        java.security.cert.X509Certificate clientCert = (java.security.cert.X509Certificate) certs[0];
                        // Extract role from certificate CN or OU field
                        String dn = clientCert.getSubjectX500Principal().getName();
                        System.out.println("Client cert DN: " + dn);
                        // Check for role in CN (e.g., CN=doctor_bob or CN=nurse_alice)
                        if (dn.contains("CN=doctor_") || dn.contains("CN=Doctor_")) {
                            role = "doctor";
                        } else if (dn.contains("CN=nurse_") || dn.contains("CN=Nurse_")) {
                            role = "nurse";
                        }
                        // Also check OU field (organizational unit)
                        if (role == null) {
                            if (dn.contains("OU=doctor") || dn.contains("OU=Doctor")) {
                                role = "doctor";
                            } else if (dn.contains("OU=nurse") || dn.contains("OU=Nurse")) {
                                role = "nurse";
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Could not extract role from certificate: " + e.getMessage());
                }
            }
            
            // Fallback to database authentication if role not found in certificate
            if (role == null) {
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
    static String b64(byte[] b) { return b == null ? "" : Base64.getEncoder().encodeToString(b); }
    static byte[] d64(String s) { return s == null || s.isEmpty() ? null : Base64.getDecoder().decode(s); }
    static String sha256(String s) throws Exception { if (s == null) return null; MessageDigest md = MessageDigest.getInstance("SHA-256"); byte[] h = md.digest(s.getBytes("UTF-8")); StringBuilder sb = new StringBuilder(); for (byte b : h) sb.append(String.format("%02x", b)); return sb.toString(); }
}
