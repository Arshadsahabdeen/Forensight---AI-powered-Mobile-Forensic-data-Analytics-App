package com.forensics.platform.dev;

import com.forensics.platform.ForensicPlatformApplication;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Development convenience: runs the backend against an embedded PostgreSQL 16 server, for machines
 * without Docker or a local PostgreSQL installation. Data persists in backend/target/dev-postgres
 * (deleted by "mvn clean").
 *
 * <pre>
 * mvn test-compile exec:java -Dexec.mainClass=com.forensics.platform.dev.LocalDevServer -Dexec.classpathScope=test
 * </pre>
 *
 * Not used in production; Docker Compose remains the supported way to run the full stack.
 */
public final class LocalDevServer {

    private LocalDevServer() {
    }

    public static void main(String[] args) throws Exception {
        Path dataDir = Path.of("target", "dev-postgres").toAbsolutePath();
        Files.createDirectories(dataDir);
        int port = Integer.parseInt(System.getProperty("dev.db.port", "54329"));
        EmbeddedPostgres postgres = EmbeddedPostgres.builder()
                .setDataDirectory(dataDir)
                .setCleanDataDirectory(false)
                .setPort(port)
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                postgres.close();
            } catch (Exception ignored) {
                // shutting down
            }
        }));
        System.setProperty("spring.datasource.url", postgres.getJdbcUrl("postgres", "postgres"));
        System.setProperty("spring.datasource.username", "postgres");
        System.setProperty("spring.datasource.password", "");
        System.setProperty("app.storage.evidence-root", Path.of("target", "dev-evidence").toAbsolutePath().toString());
        ForensicPlatformApplication.main(args);
    }
}
