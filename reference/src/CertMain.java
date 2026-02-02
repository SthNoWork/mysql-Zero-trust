public class CertMain {
    public static void main(String[] args) {
        try {
            // Delegate to CertificateSetup's main method
            CertificateSetup.main(new String[0]);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
