import java.io.FileOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.io.File;

public class KeyGen {

    public static void main(String[] args) throws Exception {

        new File("keys").mkdirs();

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();

        write("keys/public.key", pair.getPublic().getEncoded());
        write("keys/private.key", pair.getPrivate().getEncoded());

        System.out.println("Keys generated.");
    }

    private static void write(String path, byte[] key) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(path)) {
            fos.write(Base64.getEncoder().encode(key));
        }
    }
}
