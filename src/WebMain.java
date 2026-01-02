import server.SimpleWebServer;

public class WebMain {
    public static void main(String[] args) {
        try {
            SimpleWebServer server = new SimpleWebServer();
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
