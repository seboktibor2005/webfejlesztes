package hu.eszkozpont;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.SpringApplication;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;

/** Csak helyi fejlesztés: tartós PostgreSQL és a local profil indítása Docker nélkül. */
public class LocalDevelopment {
    public static void main(String[] args) throws Exception {
        Path local = Path.of("..", ".local").toAbsolutePath().normalize();
        Files.createDirectories(local);
        Path keyFile = local.resolve("jwt-secret");
        if (!Files.exists(keyFile)) {
            byte[] key = new byte[48]; new SecureRandom().nextBytes(key);
            Files.writeString(keyFile, Base64.getEncoder().encodeToString(key), StandardOpenOption.CREATE_NEW);
        }
        var postgres = EmbeddedPostgres.builder().setPort(55432)
                .setDataDirectory(local.resolve("postgres"))
                .setCleanDataDirectory(false).setServerConfig("listen_addresses", "127.0.0.1").start();
        System.setProperty("DB_URL", postgres.getJdbcUrl("postgres", "postgres"));
        System.setProperty("DB_USERNAME", "postgres");
        System.setProperty("DB_PASSWORD", "postgres");
        if (System.getenv("JWT_SECRET") == null) System.setProperty("JWT_SECRET", Files.readString(keyFile).strip());
        var application = new SpringApplication(EszkozpontApplication.class);
        application.setAdditionalProfiles("local");
        try {
            var context = application.run(args);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                context.close();
                try { postgres.close(); } catch (Exception ignored) { }
            }));
            new CountDownLatch(1).await();
        } catch (Exception exception) {
            postgres.close(); throw exception;
        }
    }
}
